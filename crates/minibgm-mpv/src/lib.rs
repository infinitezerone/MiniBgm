//! minibgm-mpv：libmpv JNI 桥（控制面 + render API）。
//!
//! 设计要点：
//! - 通过 dlopen("libmpv.so") 解析符号，零链接配置（libmpv 由 AAR 打进 APK，
//!   位于应用 nativeLibraryDir，dlopen 按库名即可命中）。
//! - 句柄自管并保证同进程同一时刻至多一个存活句柄（create 前自动 terminate
//!   旧句柄），消除"快速退出重进 → libplayer g_mpv 单例 assert 自杀"的竞态。
//! - 事件泵：独立线程阻塞 mpv_wait_event，经 Kotlin 静态方法回调
//!   （MpvNative.onEvent / onProperty*）。
//! - render API：mpv_render_context 常驻，surface 重建只是 GL 层换渲染目标，
//!   解码管线不动——这是旋转/PiP 稳定性的根基。

use jni::objects::{JClass, JObjectArray, JString, JValue};
use jni::sys::{jboolean, jdouble, jint, jlong, jobject, JNI_VERSION_1_6};
use jni::{JNIEnv, JavaVM};
use libloading::{Library, Symbol};
use std::ffi::{c_char, c_double, c_int, c_void, CStr, CString};
use std::sync::atomic::{AtomicBool, AtomicPtr, Ordering};
use std::sync::{Mutex, OnceLock};

// ---------- libmpv 常量（client.h / render.h 子集） ----------

#[allow(dead_code)]
const MPV_FORMAT_NONE: c_int = 0;
const MPV_FORMAT_STRING: c_int = 1;
const MPV_FORMAT_FLAG: c_int = 3;
const MPV_FORMAT_INT64: c_int = 4;
const MPV_FORMAT_DOUBLE: c_int = 5;

const MPV_EVENT_SHUTDOWN: c_int = 1;
const MPV_EVENT_FILE_LOADED: c_int = 8;
const MPV_EVENT_END_FILE: c_int = 7;
const MPV_EVENT_PLAYBACK_RESTART: c_int = 21;
const MPV_EVENT_PROPERTY_CHANGE: c_int = 22;

const MPV_ERROR_SUCCESS: c_int = 0;

const MPV_RENDER_PARAM_API_TYPE: c_int = 1;
const MPV_RENDER_PARAM_OPENGL_INIT_PARAMS: c_int = 2;
const MPV_RENDER_PARAM_OPENGL_FBO: c_int = 3;
const MPV_RENDER_PARAM_FLIP_Y: c_int = 4;

// ---------- 句柄与全局状态 ----------

static G_MPVP: AtomicPtr<c_void> = AtomicPtr::new(std::ptr::null_mut());
static G_RENDER_CTX: AtomicPtr<c_void> = AtomicPtr::new(std::ptr::null_mut());
static G_EVENT_THREAD_EXIT: AtomicBool = AtomicBool::new(false);
struct VmPtr(*mut JavaVM);
unsafe impl Send for VmPtr {}
unsafe impl Sync for VmPtr {}

static G_VM: OnceLock<VmPtr> = OnceLock::new();
static MPV_LIB: OnceLock<Mutex<Option<Library>>> = OnceLock::new();
static EGL_LIB: OnceLock<Mutex<Option<Library>>> = OnceLock::new();

/// 事件泵与渲染回调共用的"句柄可用"闸：destroy 先置 false 再清句柄，
/// 回调侧不会摸到已释放的句柄。
static HANDLE_VALID: AtomicBool = AtomicBool::new(false);

#[repr(C)]
struct MpvRenderParam {
    param_type: c_int,
    data: *mut c_void,
}

#[repr(C)]
struct MpvOpenGLInitParams {
    get_proc_address: unsafe extern "C" fn(*mut c_void, *const c_char) -> *mut c_void,
    get_proc_address_ctx: *mut c_void,
}

#[repr(C)]
struct MpvOpenGLFbo {
    fbo: c_int,
    w: c_int,
    h: c_int,
    internal_format: c_int,
}

// ---------- libmpv 符号解析 ----------

macro_rules! sym {
    ($lib:expr, $name:ident, $ty:ty) => {{
        let s: Symbol<$ty> = match unsafe { $lib.get(stringify!($name).as_bytes()) } {
            Ok(s) => s,
            Err(_) => return Err(format!("symbol {} not found", stringify!($name))),
        };
        *s
    }};
}

struct MpvApi {
    mpv_create: unsafe extern "C" fn() -> *mut c_void,
    mpv_initialize: unsafe extern "C" fn(*mut c_void) -> c_int,
    mpv_terminate_destroy: unsafe extern "C" fn(*mut c_void),
    mpv_set_option_string: unsafe extern "C" fn(*mut c_void, *const c_char, *const c_char) -> c_int,
    mpv_command: unsafe extern "C" fn(*mut c_void, *mut *mut c_char) -> c_int,
    mpv_get_property: unsafe extern "C" fn(*mut c_void, *const c_char, c_int, *mut c_void) -> c_int,
    mpv_set_property: unsafe extern "C" fn(*mut c_void, *const c_char, c_int, *mut c_void) -> c_int,
    mpv_observe_property: unsafe extern "C" fn(*mut c_void, u64, *const c_char, c_int) -> c_int,
    mpv_wait_event: unsafe extern "C" fn(*mut c_void, c_double) -> *mut c_void,
    mpv_render_context_create:
        unsafe extern "C" fn(*mut *mut c_void, *mut c_void, *mut MpvRenderParam) -> c_int,
    mpv_render_context_render: unsafe extern "C" fn(*mut c_void, *mut MpvRenderParam) -> c_int,
    mpv_render_context_report_swap: unsafe extern "C" fn(*mut c_void),
    mpv_render_context_set_update_callback:
        unsafe extern "C" fn(*mut c_void, Option<unsafe extern "C" fn(*mut c_void)>, *mut c_void),
    mpv_render_context_free: unsafe extern "C" fn(*mut c_void),
    mpv_free: unsafe extern "C" fn(*mut c_void),
}

unsafe impl Send for MpvApi {}
unsafe impl Sync for MpvApi {}

fn mpv_lib() -> Result<&'static Mutex<Option<Library>>, String> {
    let lock = MPV_LIB.get_or_init(|| {
        // libavutil 先行加载：av_jni_set_java_vm 在其中，且 libmpv 依赖它
        unsafe {
            if let Ok(avutil) = Library::new("libavutil.so") {
                // 保持常驻：移交所有权后永不卸载
                std::mem::forget(avutil);
            }
        }
        Mutex::new(unsafe { Library::new("libmpv.so").ok() })
    });
    let loaded = lock.lock().map(|g| g.is_some()).unwrap_or(false);
    if !loaded {
        return Err("dlopen libmpv.so failed".into());
    }
    Ok(lock)
}

fn with_api<T>(f: impl FnOnce(&MpvApi) -> T) -> Result<T, String> {
    let lock = mpv_lib()?;
    let guard = lock.lock().map_err(|_| "mpv lib mutex poisoned")?;
    let lib = match guard.as_ref() {
        Some(l) => l,
        None => return Err("mpv lib unloaded".into()),
    };
    let api = MpvApi {
        mpv_create: sym!(lib, mpv_create, unsafe extern "C" fn() -> *mut c_void),
        mpv_initialize: sym!(
            lib,
            mpv_initialize,
            unsafe extern "C" fn(*mut c_void) -> c_int
        ),
        mpv_terminate_destroy: sym!(
            lib,
            mpv_terminate_destroy,
            unsafe extern "C" fn(*mut c_void)
        ),
        mpv_set_option_string: sym!(
            lib,
            mpv_set_option_string,
            unsafe extern "C" fn(*mut c_void, *const c_char, *const c_char) -> c_int
        ),
        mpv_command: sym!(
            lib,
            mpv_command,
            unsafe extern "C" fn(*mut c_void, *mut *mut c_char) -> c_int
        ),
        mpv_get_property: sym!(
            lib,
            mpv_get_property,
            unsafe extern "C" fn(*mut c_void, *const c_char, c_int, *mut c_void) -> c_int
        ),
        mpv_set_property: sym!(
            lib,
            mpv_set_property,
            unsafe extern "C" fn(*mut c_void, *const c_char, c_int, *mut c_void) -> c_int
        ),
        mpv_observe_property: sym!(
            lib,
            mpv_observe_property,
            unsafe extern "C" fn(*mut c_void, u64, *const c_char, c_int) -> c_int
        ),
        mpv_wait_event: sym!(
            lib,
            mpv_wait_event,
            unsafe extern "C" fn(*mut c_void, c_double) -> *mut c_void
        ),
        mpv_render_context_create: sym!(
            lib,
            mpv_render_context_create,
            unsafe extern "C" fn(*mut *mut c_void, *mut c_void, *mut MpvRenderParam) -> c_int
        ),
        mpv_render_context_render: sym!(
            lib,
            mpv_render_context_render,
            unsafe extern "C" fn(*mut c_void, *mut MpvRenderParam) -> c_int
        ),
        mpv_render_context_report_swap: sym!(
            lib,
            mpv_render_context_report_swap,
            unsafe extern "C" fn(*mut c_void)
        ),
        mpv_render_context_set_update_callback: sym!(
            lib,
            mpv_render_context_set_update_callback,
            unsafe extern "C" fn(
                *mut c_void,
                Option<unsafe extern "C" fn(*mut c_void)>,
                *mut c_void,
            )
        ),
        mpv_render_context_free: sym!(
            lib,
            mpv_render_context_free,
            unsafe extern "C" fn(*mut c_void)
        ),
        mpv_free: sym!(lib, mpv_free, unsafe extern "C" fn(*mut c_void)),
    };
    Ok(f(&api))
}

unsafe fn mpv_handle() -> Result<*mut c_void, String> {
    let p = G_MPVP.load(Ordering::Acquire);
    if p.is_null() {
        Err("mpv handle is null".into())
    } else {
        Ok(p)
    }
}

// ---------- get_proc_address（render API 的 GL 符号解析） ----------

unsafe extern "C" fn gl_proc_address(_ctx: *mut c_void, name: *const c_char) -> *mut c_void {
    // EGL 已随 SurfaceView 的 GL 上下文加载：dlsym(RTLD_DEFAULT) 直接命中
    let sym = unsafe { dlsym(std::ptr::null_mut(), name) };
    if !sym.is_null() {
        return sym;
    }
    let lock = EGL_LIB.get_or_init(|| Mutex::new(unsafe { Library::new("libEGL.so").ok() }));
    let guard = match lock.lock() {
        Ok(g) => g,
        Err(_) => return std::ptr::null_mut(),
    };
    let lib = match guard.as_ref() {
        Some(l) => l,
        None => return std::ptr::null_mut(),
    };
    let proc_sym: Symbol<unsafe extern "C" fn(*const c_char) -> *mut c_void> =
        match lib.get(b"eglGetProcAddress") {
            Ok(s) => s,
            Err(_) => return std::ptr::null_mut(),
        };
    unsafe { proc_sym(name) }
}

extern "C" {
    #[link_name = "dlsym"]
    fn dlsym(handle: *mut c_void, symbol: *const c_char) -> *mut c_void;
}

// ---------- Kotlin 回调（MpvNative 的静态方法） ----------

fn call_static(env: &mut JNIEnv, name: &str, sig: &str, args: &[JValue]) {
    let class = match env.find_class("com/infinitezerone/minibgm/feature/subject/player/MpvNative")
    {
        Ok(c) => c,
        Err(_) => return,
    };
    let _ = env.call_static_method(class, name, sig, args);
}

fn dispatch_property(env: &mut JNIEnv, name: &str, format: c_int, data: *mut c_void) {
    let jname = match env.new_string(name) {
        Ok(s) => s,
        Err(_) => return,
    };
    unsafe {
        match format {
            MPV_FORMAT_FLAG => {
                let v = *(data as *mut c_int) != 0;
                call_static(
                    env,
                    "onPropertyFlag",
                    "(Ljava/lang/String;Z)V",
                    &[JValue::Object(&jname), JValue::Bool(v as u8)],
                );
            }
            MPV_FORMAT_INT64 => {
                let v = *(data as *mut i64);
                call_static(
                    env,
                    "onPropertyLong",
                    "(Ljava/lang/String;J)V",
                    &[JValue::Object(&jname), JValue::Long(v)],
                );
            }
            MPV_FORMAT_DOUBLE => {
                let v = *(data as *mut f64);
                call_static(
                    env,
                    "onPropertyDouble",
                    "(Ljava/lang/String;D)V",
                    &[JValue::Object(&jname), JValue::Double(v)],
                );
            }
            MPV_FORMAT_STRING => {
                let ptr = *(data as *mut *const c_char);
                if ptr.is_null() {
                    return;
                }
                let s = CStr::from_ptr(ptr).to_string_lossy();
                if let Ok(jv) = env.new_string(s.as_ref()) {
                    call_static(
                        env,
                        "onPropertyString",
                        "(Ljava/lang/String;Ljava/lang/String;)V",
                        &[JValue::Object(&jname), JValue::Object(&jv)],
                    );
                }
            }
            _ => {
                call_static(
                    env,
                    "onPropertyNone",
                    "(Ljava/lang/String;)V",
                    &[JValue::Object(&jname)],
                );
            }
        }
    }
}

// ---------- 事件泵 ----------

fn event_thread() {
    let VmPtr(vm_ptr) = match G_VM.get() {
        Some(p) => p,
        None => return,
    };
    let vm = unsafe { &**vm_ptr };
    let mut guard = match vm.attach_current_thread() {
        Ok(g) => g,
        Err(_) => return,
    };
    let env: &mut JNIEnv = &mut guard;

    loop {
        if G_EVENT_THREAD_EXIT.load(Ordering::Acquire) {
            break;
        }
        let handle = match unsafe { mpv_handle() } {
            Ok(h) => h,
            Err(_) => break,
        };
        if !HANDLE_VALID.load(Ordering::Acquire) {
            break;
        }
        let event = match with_api(|api| unsafe { (api.mpv_wait_event)(handle, -1.0) }) {
            Ok(e) => e,
            Err(_) => break,
        };
        if G_EVENT_THREAD_EXIT.load(Ordering::Acquire) {
            break;
        }
        unsafe {
            // mpv_event { event_id@0, error@4, reply_userdata@8, data@16 }
            let event_id = *(event as *const c_int);
            match event_id {
                MPV_EVENT_PROPERTY_CHANGE => {
                    let prop = *(event as *const *mut c_void).add(2);
                    if prop.is_null() {
                        continue;
                    }
                    // mpv_event_property { name@0, format@8, data@16 }
                    let name_ptr = *(prop as *const *const c_char);
                    let format = *(prop.add(8) as *const c_int);
                    let data = prop.add(16);
                    if name_ptr.is_null() {
                        continue;
                    }
                    let name = match CStr::from_ptr(name_ptr).to_str() {
                        Ok(s) => s.to_string(),
                        Err(_) => continue,
                    };
                    dispatch_property(env, &name, format, data);
                }
                MPV_EVENT_SHUTDOWN => {
                    HANDLE_VALID.store(false, Ordering::Release);
                    call_static(env, "onEvent", "(I)V", &[JValue::Int(MPV_EVENT_SHUTDOWN)]);
                    break;
                }
                MPV_EVENT_FILE_LOADED | MPV_EVENT_END_FILE | MPV_EVENT_PLAYBACK_RESTART => {
                    call_static(env, "onEvent", "(I)V", &[JValue::Int(event_id)]);
                }
                _ => {}
            }
        }
    }
}

// ---------- JNI 导出 ----------

#[no_mangle]
pub extern "system" fn JNI_OnLoad(vm: *mut JavaVM, _reserved: *mut c_void) -> jint {
    if !vm.is_null() {
        let _ = G_VM.set(VmPtr(vm));
    }
    JNI_VERSION_1_6
}

/// 创建句柄；同进程重复 create（旧句柄未销毁）自动 terminate 旧句柄，
/// 消除"快速退出重进 → 进程级单例 assert 自杀"的竞态。
#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_create(
    env: JNIEnv,
    _class: JClass,
    _appctx: jobject,
) {
    let _ = with_api(|api| {
        unsafe {
            let old = G_MPVP.swap(std::ptr::null_mut(), Ordering::AcqRel);
            if !old.is_null() {
                HANDLE_VALID.store(false, Ordering::Release);
                G_EVENT_THREAD_EXIT.store(true, Ordering::Release);
                (api.mpv_terminate_destroy)(old);
            }
            G_RENDER_CTX.store(std::ptr::null_mut(), Ordering::Release);

            let handle = (api.mpv_create)();
            if handle.is_null() {
                return;
            }
            G_MPVP.store(handle, Ordering::Release);
            HANDLE_VALID.store(true, Ordering::Release);

            // ffmpeg JNI 桥（MediaCodec 硬解需要 JavaVM）
            if let Some(VmPtr(vm_ptr)) = G_VM.get() {
                let vm = &**vm_ptr;
                if let Ok(avutil) = Library::new("libavutil.so") {
                    let set_vm: Symbol<unsafe extern "C" fn(*mut c_void, *mut c_void) -> c_int> =
                        match avutil.get(b"av_jni_set_java_vm") {
                            Ok(s) => s,
                            Err(_) => {
                                std::mem::forget(avutil);
                                return;
                            }
                        };
                    (set_vm)(
                        vm.get_java_vm_pointer().cast::<c_void>(),
                        std::ptr::null_mut(),
                    );
                    std::mem::forget(avutil);
                }
            }
            let _ = env;
        }
    });
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_init(
    _env: JNIEnv,
    _class: JClass,
) {
    let _ = with_api(|api| unsafe {
        let handle = match mpv_handle() {
            Ok(h) => h,
            Err(_) => return,
        };
        if (api.mpv_initialize)(handle) < 0 {
            return;
        }
        G_EVENT_THREAD_EXIT.store(false, Ordering::Release);
        std::thread::Builder::new()
            .name("mpv-event".into())
            .spawn(event_thread)
            .ok();
    });
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_destroy(
    _env: JNIEnv,
    _class: JClass,
) {
    let _ = with_api(|api| unsafe {
        // render context 先于核心释放
        let ctx = G_RENDER_CTX.swap(std::ptr::null_mut(), Ordering::AcqRel);
        if !ctx.is_null() {
            (api.mpv_render_context_free)(ctx);
        }
        let handle = G_MPVP.swap(std::ptr::null_mut(), Ordering::AcqRel);
        if handle.is_null() {
            return;
        }
        HANDLE_VALID.store(false, Ordering::Release);
        G_EVENT_THREAD_EXIT.store(true, Ordering::Release);
        (api.mpv_terminate_destroy)(handle);
    });
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_command(
    mut env: JNIEnv,
    _class: JClass,
    args: JObjectArray,
) {
    let _ = with_api(|api| unsafe {
        let handle = match mpv_handle() {
            Ok(h) => h,
            Err(_) => return,
        };
        let count = env.get_array_length(&args).unwrap_or(0);
        let mut c_args: Vec<CString> = Vec::with_capacity(count as usize);
        for i in 0..count {
            let item = match env.get_object_array_element(&args, i) {
                Ok(o) => JString::from(o),
                Err(_) => return,
            };
            let s: String = match env.get_string(&item) {
                Ok(s) => s.to_string_lossy().into_owned(),
                Err(_) => return,
            };
            match CString::new(s) {
                Ok(c) => c_args.push(c),
                Err(_) => return,
            }
        }
        let mut ptrs: Vec<*mut c_char> = c_args.iter().map(|s| s.as_ptr() as *mut c_char).collect();
        ptrs.push(std::ptr::null_mut());
        (api.mpv_command)(handle, ptrs.as_mut_ptr());
    });
}

fn jstr_to_cstring(env: &mut JNIEnv, s: &JString) -> Result<CString, String> {
    let owned: String = env
        .get_string(s)
        .map_err(|_| "get_string failed")?
        .to_string_lossy()
        .into_owned();
    CString::new(owned).map_err(|_| "interior nul".into())
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_setOptionString(
    mut env: JNIEnv,
    _class: JClass,
    name: JString,
    value: JString,
) -> jint {
    let result = (|| {
        let n = jstr_to_cstring(&mut env, &name)?;
        let v = jstr_to_cstring(&mut env, &value)?;
        with_api(|api| unsafe {
            let handle = match mpv_handle() {
                Ok(h) => h,
                Err(_) => return Err("no handle".into()),
            };
            Ok((api.mpv_set_option_string)(handle, n.as_ptr(), v.as_ptr()))
        })
    })();
    result.and_then(|r| r).unwrap_or(-1)
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_getPropertyInt(
    mut env: JNIEnv,
    _class: JClass,
    name: JString,
) -> jlong {
    let result = (|| {
        let n = jstr_to_cstring(&mut env, &name)?;
        with_api(|api| unsafe {
            let handle = mpv_handle()?;
            let mut out: i64 = i64::MIN;
            let rc = (api.mpv_get_property)(
                handle,
                n.as_ptr(),
                MPV_FORMAT_INT64,
                std::ptr::from_mut(&mut out).cast::<c_void>(),
            );
            if rc != MPV_ERROR_SUCCESS {
                return Err("get failed".into());
            }
            Ok(out)
        })
    })();
    result.and_then(|r| r).unwrap_or(i64::MIN)
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_getPropertyDouble(
    mut env: JNIEnv,
    _class: JClass,
    name: JString,
) -> jdouble {
    let result = (|| {
        let n = jstr_to_cstring(&mut env, &name)?;
        with_api(|api| unsafe {
            let handle = mpv_handle()?;
            let mut out: f64 = f64::NAN;
            let rc = (api.mpv_get_property)(
                handle,
                n.as_ptr(),
                MPV_FORMAT_DOUBLE,
                std::ptr::from_mut(&mut out).cast::<c_void>(),
            );
            if rc != MPV_ERROR_SUCCESS {
                return Err("get failed".into());
            }
            Ok(out)
        })
    })();
    result.and_then(|r| r).unwrap_or(f64::NAN)
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_getPropertyBoolean(
    mut env: JNIEnv,
    _class: JClass,
    name: JString,
) -> jboolean {
    let result = (|| {
        let n = jstr_to_cstring(&mut env, &name)?;
        with_api(|api| unsafe {
            let handle = mpv_handle()?;
            let mut out: c_int = 0;
            let rc = (api.mpv_get_property)(
                handle,
                n.as_ptr(),
                MPV_FORMAT_FLAG,
                std::ptr::from_mut(&mut out).cast::<c_void>(),
            );
            if rc != MPV_ERROR_SUCCESS {
                return Err("get failed".into());
            }
            Ok(out)
        })
    })();
    let flat = result.and_then(|r| r);
    match flat {
        Ok(v) => (v != 0) as jboolean,
        Err(_) => 2u8, // 2 = 失败哨兵，Kotlin 侧映射为 null
    }
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_getPropertyString(
    mut env: JNIEnv,
    _class: JClass,
    name: JString,
) -> jobject {
    let result = (|| {
        let n = jstr_to_cstring(&mut env, &name)?;
        with_api(|api| unsafe {
            let handle = mpv_handle()?;
            let mut out: *mut c_char = std::ptr::null_mut();
            let rc = (api.mpv_get_property)(
                handle,
                n.as_ptr(),
                MPV_FORMAT_STRING,
                std::ptr::from_mut(&mut out).cast::<c_void>(),
            );
            if rc != MPV_ERROR_SUCCESS || out.is_null() {
                return Err("get failed".into());
            }
            let s = CStr::from_ptr(out).to_string_lossy().into_owned();
            (api.mpv_free)(out as *mut c_void);
            let j = env.new_string(s).map_err(|_| "new_string failed")?;
            Ok(j.into_raw())
        })
    })();
    let flat = result.and_then(|r| r);
    match flat {
        Ok(ptr) => ptr,
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_setPropertyInt(
    mut env: JNIEnv,
    _class: JClass,
    name: JString,
    value: jlong,
) {
    let result = (|| {
        let n = jstr_to_cstring(&mut env, &name)?;
        let mut v: i64 = value;
        with_api(|api| -> Result<(), String> {
            unsafe {
                let handle = match mpv_handle() {
                    Ok(h) => h,
                    Err(_) => return Err("no handle".into()),
                };
                (api.mpv_set_property)(
                    handle,
                    n.as_ptr(),
                    MPV_FORMAT_INT64,
                    std::ptr::from_mut(&mut v).cast::<c_void>(),
                );
                Ok(())
            }
        })
    })();
    let _ = result;
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_setPropertyDouble(
    mut env: JNIEnv,
    _class: JClass,
    name: JString,
    value: jdouble,
) {
    let result = (|| {
        let n = jstr_to_cstring(&mut env, &name)?;
        let mut v: f64 = value;
        with_api(|api| -> Result<(), String> {
            unsafe {
                let handle = match mpv_handle() {
                    Ok(h) => h,
                    Err(_) => return Err("no handle".into()),
                };
                (api.mpv_set_property)(
                    handle,
                    n.as_ptr(),
                    MPV_FORMAT_DOUBLE,
                    std::ptr::from_mut(&mut v).cast::<c_void>(),
                );
                Ok(())
            }
        })
    })();
    let _ = result;
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_setPropertyBoolean(
    mut env: JNIEnv,
    _class: JClass,
    name: JString,
    value: jboolean,
) {
    let result = (|| {
        let n = jstr_to_cstring(&mut env, &name)?;
        let mut v: c_int = (value != 0) as c_int;
        with_api(|api| -> Result<(), String> {
            unsafe {
                let handle = match mpv_handle() {
                    Ok(h) => h,
                    Err(_) => return Err("no handle".into()),
                };
                (api.mpv_set_property)(
                    handle,
                    n.as_ptr(),
                    MPV_FORMAT_FLAG,
                    std::ptr::from_mut(&mut v).cast::<c_void>(),
                );
                Ok(())
            }
        })
    })();
    let _ = result;
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_setPropertyString(
    mut env: JNIEnv,
    _class: JClass,
    name: JString,
    value: JString,
) {
    let result = (|| {
        let n = jstr_to_cstring(&mut env, &name)?;
        let v = jstr_to_cstring(&mut env, &value)?;
        with_api(|api| -> Result<(), String> {
            unsafe {
                let handle = match mpv_handle() {
                    Ok(h) => h,
                    Err(_) => return Err("no handle".into()),
                };
                (api.mpv_set_property)(
                    handle,
                    n.as_ptr(),
                    MPV_FORMAT_STRING,
                    v.as_ptr() as *mut c_void,
                );
                Ok(())
            }
        })
    })();
    let _ = result;
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_observeProperty(
    mut env: JNIEnv,
    _class: JClass,
    name: JString,
    format: jint,
) {
    let result = (|| {
        let n = jstr_to_cstring(&mut env, &name)?;
        with_api(|api| -> Result<(), String> {
            unsafe {
                let handle = match mpv_handle() {
                    Ok(h) => h,
                    Err(_) => return Err("no handle".into()),
                };
                (api.mpv_observe_property)(handle, 0, n.as_ptr(), format);
                Ok(())
            }
        })
    })();
    let _ = result;
}

// ---------- render API ----------

unsafe extern "C" fn render_update_callback(_ctx: *mut c_void) {
    if let Some(VmPtr(vm_ptr)) = G_VM.get() {
        let vm = unsafe { &**vm_ptr };
        if let Ok(mut guard) = vm.attach_current_thread() {
            let env: &mut JNIEnv = &mut guard;
            call_static(env, "onRenderUpdate", "()V", &[]);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_renderContextCreate(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    // 返回 true 表示 render context 可用；false 时调用方需等待 mpv 就绪后重试
    let result = with_api(|api| unsafe {
        let handle = match mpv_handle() {
            Ok(h) => h,
            // 特殊值 -2：mpv 句柄尚未创建（视图早于引擎初始化的正常时序）
            Err(_) => return -2,
        };
        // 幂等：已存在先释放（GL 上下文重建场景）
        let old = G_RENDER_CTX.swap(std::ptr::null_mut(), Ordering::AcqRel);
        if !old.is_null() {
            (api.mpv_render_context_free)(old);
        }
        let mut ctx: *mut c_void = std::ptr::null_mut();
        let init_params = MpvOpenGLInitParams {
            get_proc_address: gl_proc_address,
            get_proc_address_ctx: std::ptr::null_mut(),
        };
        let api_type = CString::new("opengl").unwrap_or_default();
        let mut params = [
            MpvRenderParam {
                param_type: MPV_RENDER_PARAM_API_TYPE,
                data: api_type.as_ptr() as *mut c_void,
            },
            MpvRenderParam {
                param_type: MPV_RENDER_PARAM_OPENGL_INIT_PARAMS,
                data: &init_params as *const MpvOpenGLInitParams as *mut c_void,
            },
            MpvRenderParam {
                param_type: 0,
                data: std::ptr::null_mut(),
            },
        ];
        let rc = (api.mpv_render_context_create)(&mut ctx, handle, params.as_mut_ptr());
        if rc < 0 {
            return rc;
        }
        G_RENDER_CTX.store(ctx, Ordering::Release);
        (api.mpv_render_context_set_update_callback)(
            ctx,
            Some(render_update_callback),
            std::ptr::null_mut(),
        );
        0
    });
    match result {
        Ok(0) => 1u8,
        _ => 0u8,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_renderContextRender(
    _env: JNIEnv,
    _class: JClass,
    width: jint,
    height: jint,
) {
    let ctx = G_RENDER_CTX.load(Ordering::Acquire);
    if ctx.is_null() {
        return;
    }
    let _ = with_api(|api| unsafe {
        let fbo = MpvOpenGLFbo {
            fbo: 0,
            w: width,
            h: height,
            internal_format: 0,
        };
        let flip: c_int = 1;
        let mut params = [
            MpvRenderParam {
                param_type: MPV_RENDER_PARAM_OPENGL_FBO,
                data: &fbo as *const MpvOpenGLFbo as *mut c_void,
            },
            MpvRenderParam {
                param_type: MPV_RENDER_PARAM_FLIP_Y,
                data: &flip as *const c_int as *mut c_void,
            },
            MpvRenderParam {
                param_type: 0,
                data: std::ptr::null_mut(),
            },
        ];
        (api.mpv_render_context_render)(ctx, params.as_mut_ptr());
    });
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_renderContextReportSwap(
    _env: JNIEnv,
    _class: JClass,
) {
    let ctx = G_RENDER_CTX.load(Ordering::Acquire);
    if ctx.is_null() {
        return;
    }
    let _ = with_api(|api| unsafe { (api.mpv_render_context_report_swap)(ctx) });
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_renderContextRelease(
    _env: JNIEnv,
    _class: JClass,
) {
    let ctx = G_RENDER_CTX.swap(std::ptr::null_mut(), Ordering::AcqRel);
    if ctx.is_null() {
        return;
    }
    let _ = with_api(|api| unsafe { (api.mpv_render_context_free)(ctx) });
}
