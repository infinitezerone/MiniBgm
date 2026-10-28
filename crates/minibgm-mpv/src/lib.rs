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

use jni::objects::{GlobalRef, JClass, JObjectArray, JString, JValue};
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
const MPV_EVENT_LOG_MESSAGE: c_int = 2;
const MPV_EVENT_FILE_LOADED: c_int = 8;
const MPV_EVENT_END_FILE: c_int = 7;
const MPV_EVENT_PLAYBACK_RESTART: c_int = 21;
const MPV_EVENT_PROPERTY_CHANGE: c_int = 22;

const MPV_ERROR_SUCCESS: c_int = 0;

const MPV_RENDER_PARAM_API_TYPE: c_int = 1;
const MPV_RENDER_PARAM_OPENGL_INIT_PARAMS: c_int = 2;
const MPV_RENDER_PARAM_OPENGL_FBO: c_int = 3;
const MPV_RENDER_PARAM_FLIP_Y: c_int = 4;

/// mpv_render_context_update() 返回值：确实有新帧可画。
/// 没有这一位就说明本次唤醒只是状态变化，不应重绘（否则会画出空帧白耗 GPU/CPU）。
const MPV_RENDER_UPDATE_FRAME: u64 = 1;

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

/// 已解析的 mpv 符号表。libmpv 常驻不卸载，函数指针全进程有效。
/// 不缓存的话每帧 render/report_swap 都要 dlsym 二十多个符号并抢一次锁。
static API: OnceLock<MpvApi> = OnceLock::new();

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
    mpv_request_log_messages: unsafe extern "C" fn(*mut c_void, *const c_char) -> c_int,
    mpv_wait_event: unsafe extern "C" fn(*mut c_void, c_double) -> *mut c_void,
    mpv_render_context_create:
        unsafe extern "C" fn(*mut *mut c_void, *mut c_void, *mut MpvRenderParam) -> c_int,
    mpv_render_context_render: unsafe extern "C" fn(*mut c_void, *mut MpvRenderParam) -> c_int,
    mpv_render_context_report_swap: unsafe extern "C" fn(*mut c_void),
    mpv_render_context_update: unsafe extern "C" fn(*mut c_void) -> u64,
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

fn cached_api() -> Result<&'static MpvApi, String> {
    if let Some(api) = API.get() {
        return Ok(api);
    }
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
        mpv_request_log_messages: sym!(
            lib,
            mpv_request_log_messages,
            unsafe extern "C" fn(*mut c_void, *const c_char) -> c_int
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
        mpv_render_context_update: sym!(
            lib,
            mpv_render_context_update,
            unsafe extern "C" fn(*mut c_void) -> u64
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
    let _ = API.set(api);
    API.get().ok_or_else(|| "api cache init failed".to_string())
}

fn with_api<T>(f: impl FnOnce(&MpvApi) -> T) -> Result<T, String> {
    Ok(f(cached_api()?))
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

    #[link_name = "dlopen"]
    fn dlopen(filename: *const c_char, flags: c_int) -> *mut c_void;
}

const RTLD_LAZY: c_int = 0x0001;

#[repr(C)]
struct DlInfo {
    dli_fname: *const c_char,
    dli_fbase: *mut c_void,
    dli_sname: *const c_char,
    dli_saddr: *mut c_void,
}

extern "C" {
    fn dladdr(addr: *const c_void, info: *mut DlInfo) -> c_int;
}

/// 本 .so 所在目录（= APK 的 nativeLibraryDir）
fn own_library_dir() -> Option<String> {
    unsafe {
        let mut info = DlInfo {
            dli_fname: std::ptr::null(),
            dli_fbase: std::ptr::null_mut(),
            dli_sname: std::ptr::null(),
            dli_saddr: std::ptr::null_mut(),
        };
        let probe = JNI_OnLoad as *const () as *const c_void;
        if dladdr(probe, &mut info) == 0 || info.dli_fname.is_null() {
            return None;
        }
        let path = CStr::from_ptr(info.dli_fname)
            .to_string_lossy()
            .into_owned();
        Some(path.rsplit_once('/')?.0.to_string())
    }
}

/// 从**已加载**的 libavutil 里取 av_jni_set_java_vm。
///
/// 不能自己 `Library::new("libavutil.so")`：那会另开一份实例，JavaVM 注到别处，
/// ffmpeg 侧仍然报 `No Java virtual machine has been registered`（硬解全废）。
/// Android 上 RTLD_DEFAULT 只覆盖全局符号表，libavutil 是 libmpv 的私有依赖，
/// 因此按**绝对路径**打开同一份文件，链接器会复用已加载实例。
/// 在已加载的 ffmpeg 库里解析符号。
///
/// 这份 ffmpeg 把 jni.c 编进了 **libavcodec**（不是 libavutil）——
/// 用 ELF 动态符号表核实过：libavcodec.so 导出 av_jni_{set,get}_java_vm
/// 与 av_jni_set_android_app_ctx。找错库就会静默拿不到符号，硬解全废。
fn resolve_avcodec_symbol(name: &str) -> *mut c_void {
    const CANDIDATES: [&str; 2] = ["libavcodec.so", "libavutil.so"];
    let Ok(cname) = CString::new(name) else {
        return std::ptr::null_mut();
    };
    unsafe {
        // ① 全局符号表
        let mut p = dlsym(std::ptr::null_mut(), cname.as_ptr());
        // ② 按 soname 逐个库试（链接器会复用 libmpv 已加载的同一实例）
        if p.is_null() {
            for lib in CANDIDATES {
                let Ok(soname) = CString::new(lib) else {
                    continue;
                };
                let handle = dlopen(soname.as_ptr(), RTLD_LAZY);
                if handle.is_null() {
                    continue;
                }
                p = dlsym(handle, cname.as_ptr());
                if !p.is_null() {
                    break;
                }
            }
        }
        // ③ 回退：按自身所在目录拼绝对路径（库被解压到磁盘的老设备）
        if p.is_null() {
            if let Some(dir) = own_library_dir() {
                for lib in CANDIDATES {
                    if let Ok(full) = CString::new(format!("{dir}/{lib}")) {
                        let handle = dlopen(full.as_ptr(), RTLD_LAZY);
                        if !handle.is_null() {
                            p = dlsym(handle, cname.as_ptr());
                            if !p.is_null() {
                                break;
                            }
                        }
                    }
                }
            }
        }
        p
    }
}

// ---------- Kotlin 回调（MpvNative 的静态方法） ----------

// 原生诊断日志：直接打 liblog，不依赖 log crate 是否已初始化
// （事件泵/渲染回调都是原生线程，必须能独立取证）。
// 仅 Android 目标链接 liblog：宿主（Windows/macOS）跑 cargo test 时没有 log.lib，
// 不加门控会在链接期直接失败。
#[cfg(target_os = "android")]
#[link(name = "log")]
extern "C" {
    fn __android_log_write(prio: c_int, tag: *const c_char, text: *const c_char) -> c_int;
}

#[cfg(target_os = "android")]
const ANDROID_LOG_DEBUG: c_int = 3;

fn nlog(msg: &str) {
    #[cfg(target_os = "android")]
    {
        let Ok(text) = CString::new(msg) else {
            return;
        };
        let tag = CString::new("Bgm/MpvNative").unwrap_or_default();
        unsafe {
            __android_log_write(ANDROID_LOG_DEBUG, tag.as_ptr(), text.as_ptr());
        }
    }
    // 非 Android 目标（宿主单测）无 liblog：保持符号可用即可
    #[cfg(not(target_os = "android"))]
    let _ = msg;
}

/// 回调目标类：原生线程（事件泵 / 渲染唤醒）不能用 `FindClass` 定位 app 类——
/// 经 `AttachCurrentThread` 挂上来的线程走系统类加载器，查找必然失败且静默返回 null，
/// 表现为「有声音、零事件、画面停在最初两帧」。故在 Java 线程入口（JNI_OnLoad /
/// create / init）缓存一份全局引用，原生线程只认这份缓存。
const MPV_NATIVE_CLASS: &str = "com/infinitezerone/minibgm/feature/subject/player/MpvNative";

static G_CLASS: OnceLock<GlobalRef> = OnceLock::new();

/// 仅在 Java 线程调用（env 带有 app 类加载器上下文）
fn cache_class(env: &mut JNIEnv) {
    if G_CLASS.get().is_some() {
        return;
    }
    if let Ok(class) = env.find_class(MPV_NATIVE_CLASS) {
        if let Ok(global) = env.new_global_ref(class) {
            let _ = G_CLASS.set(global);
        }
    }
}

fn call_static(env: &mut JNIEnv, name: &str, sig: &str, args: &[JValue]) {
    let class = match G_CLASS.get() {
        Some(global) => unsafe { JClass::from_raw(global.as_obj().as_raw().cast()) },
        // 兜底：Java 线程直接回调时 find_class 是可靠的
        None => match env.find_class(MPV_NATIVE_CLASS) {
            Ok(c) => c,
            Err(_) => return,
        },
    };
    if env.call_static_method(class, name, sig, args).is_err() {
        nlog(&format!("call_static 失败: {name}"));
    }
}

fn dispatch_property(env: &mut JNIEnv, name: &str, format: c_int, data: *mut c_void) {
    let jname = match env.new_string(name) {
        Ok(s) => s,
        Err(_) => return,
    };
    // data 是 mpv_event_property.data 字段的地址，先取出它指向的**值指针**再解引用；
    // 少了这一步拿到的是指针自身字节，会变成随机数值（duration/time-pos/pause 全部错乱）。
    let value_ptr = unsafe { *(data as *const *mut c_void) };
    if value_ptr.is_null() {
        return;
    }
    unsafe {
        match format {
            MPV_FORMAT_FLAG => {
                let v = *(value_ptr as *const c_int) != 0;
                call_static(
                    env,
                    "onPropertyFlag",
                    "(Ljava/lang/String;Z)V",
                    &[JValue::Object(&jname), JValue::Bool(v as u8)],
                );
            }
            MPV_FORMAT_INT64 => {
                let v = *(value_ptr as *const i64);
                call_static(
                    env,
                    "onPropertyLong",
                    "(Ljava/lang/String;J)V",
                    &[JValue::Object(&jname), JValue::Long(v)],
                );
            }
            MPV_FORMAT_DOUBLE => {
                let v = *(value_ptr as *const f64);
                call_static(
                    env,
                    "onPropertyDouble",
                    "(Ljava/lang/String;D)V",
                    &[JValue::Object(&jname), JValue::Double(v)],
                );
            }
            MPV_FORMAT_STRING => {
                if value_ptr.is_null() {
                    return;
                }
                let s = CStr::from_ptr(value_ptr as *const c_char).to_string_lossy();
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
    nlog("event_thread: 启动");
    let VmPtr(vm_ptr) = match G_VM.get() {
        Some(p) => p,
        None => {
            nlog("event_thread: G_VM 未初始化，退出");
            return;
        }
    };
    let vm = match unsafe { JavaVM::from_raw(vm_ptr.cast()) } {
        Ok(v) => v,
        Err(e) => {
            nlog(&format!("event_thread: JavaVM 指针无效 {e:?}"));
            return;
        }
    };
    let mut guard = match vm.attach_current_thread() {
        Ok(g) => {
            nlog("event_thread: attach JavaVM 成功");
            g
        }
        Err(e) => {
            nlog(&format!("event_thread: attach JavaVM 失败 {e:?}"));
            return;
        }
    };
    let env: &mut JNIEnv = &mut guard;

    // 先把阻塞等待用的函数指针取出来，**不能**在持锁状态下调 mpv_wait_event：
    // with_api 每次都会 lock 住 lib 的 Mutex，而 mpv_wait_event(-1.0) 会一直阻塞，
    // 主线程随后转发 loadfile 等命令时就永久等锁（表现为整个播放页 ANR）。
    let wait_event = match with_api(|api| api.mpv_wait_event) {
        Ok(f) => f,
        Err(e) => {
            nlog(&format!("event_thread: 取 mpv_wait_event 失败 {e}"));
            return;
        }
    };

    loop {
        if G_EVENT_THREAD_EXIT.load(Ordering::Acquire) {
            nlog("event_thread: 收到退出标志");
            break;
        }
        let handle = match unsafe { mpv_handle() } {
            Ok(h) => h,
            Err(e) => {
                nlog(&format!("event_thread: 句柄不可用 {e}"));
                break;
            }
        };
        if !HANDLE_VALID.load(Ordering::Acquire) {
            nlog("event_thread: HANDLE_VALID 已置 false，退出");
            break;
        }
        let event = unsafe { wait_event(handle, -1.0) };
        if event.is_null() {
            nlog("event_thread: mpv_wait_event 返回空指针");
            break;
        }
        if G_EVENT_THREAD_EXIT.load(Ordering::Acquire) {
            nlog("event_thread: 收到退出标志（唤醒后）");
            break;
        }
        unsafe {
            // mpv_event { event_id@0, error@4, reply_userdata@8, data@16 }
            let event_id = *(event as *const c_int);
            if event_id == MPV_EVENT_LOG_MESSAGE {
                // mpv_event_log_message { prefix@0, level@8, text@16, log_level@24 }
                let data = *(event as *const *mut c_void).add(2);
                if !data.is_null() {
                    let prefix = *(data as *const *const c_char);
                    let level = *(data.add(8) as *const *const c_char);
                    let text = *(data.add(16) as *const *const c_char);
                    let p = if prefix.is_null() {
                        "mpv".to_string()
                    } else {
                        CStr::from_ptr(prefix).to_string_lossy().into_owned()
                    };
                    let l = if level.is_null() {
                        "?".to_string()
                    } else {
                        CStr::from_ptr(level).to_string_lossy().into_owned()
                    };
                    let t = if text.is_null() {
                        String::new()
                    } else {
                        CStr::from_ptr(text).to_string_lossy().trim_end().to_owned()
                    };
                    nlog(&format!("mpv[{l}] {p}: {t}"));
                }
                continue;
            }
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
    nlog("event_thread: 已退出");
}

// ---------- JNI 导出 ----------

#[no_mangle]
pub extern "system" fn JNI_OnLoad(vm: *mut JavaVM, _reserved: *mut c_void) -> jint {
    if !vm.is_null() {
        let _ = G_VM.set(VmPtr(vm));
        // 此刻由 System.loadLibrary(MpvNative 静态初始化) 触发，
        // 当前线程的 FindClass 带 app 类加载器上下文——缓存回调类的最佳时机
        if let Ok(vm_ref) = unsafe { JavaVM::from_raw(vm as *mut jni::sys::JavaVM) } {
            match vm_ref.get_env() {
                Ok(mut env) => {
                    cache_class(&mut env);
                    nlog(&format!(
                        "JNI_OnLoad: 回调类缓存={}",
                        G_CLASS.get().is_some()
                    ));
                }
                Err(e) => nlog(&format!("JNI_OnLoad: get_env 失败 {e:?}")),
            }
        }
    }
    JNI_VERSION_1_6
}

/// 创建句柄；同进程重复 create（旧句柄未销毁）自动 terminate 旧句柄，
/// 消除"快速退出重进 → 进程级单例 assert 自杀"的竞态。
#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_create(
    mut env: JNIEnv,
    _class: JClass,
    appctx: jobject,
) {
    // Java 线程上下文：兜底缓存回调类（JNI_OnLoad 未就绪时也能补上）
    cache_class(&mut env);
    let _ = with_api(|api| {
        unsafe {
            let old = G_MPVP.swap(std::ptr::null_mut(), Ordering::AcqRel);
            if !old.is_null() {
                HANDLE_VALID.store(false, Ordering::Release);
                G_EVENT_THREAD_EXIT.store(true, Ordering::Release);
                // render context 必须先于核心释放，否则 terminate_destroy 时它会踩到已析构的核
                let stale = G_RENDER_CTX.swap(std::ptr::null_mut(), Ordering::AcqRel);
                if !stale.is_null() {
                    (api.mpv_render_context_free)(stale);
                }
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
            //
            // 关键：必须打到 **libmpv 实际链接那一份 libavutil** 上。这里优先用
            // RTLD_DEFAULT / RTLD_NOLOAD 取已加载实例；直接 Library::new 可能另开一份，
            // 结果就是 ffmpeg 一直报 `No Java virtual machine has been registered`。
            if let Some(VmPtr(vm_ptr)) = G_VM.get() {
                type JniSetter = unsafe extern "C" fn(*mut c_void, *mut c_void) -> c_int;
                let set_vm = resolve_avcodec_symbol("av_jni_set_java_vm");
                if set_vm.is_null() {
                    nlog("av_jni_set_java_vm 未找到：hwdec 会退化为软解");
                } else {
                    let f: JniSetter = std::mem::transmute(set_vm);
                    f(vm_ptr.cast::<c_void>(), std::ptr::null_mut());
                    nlog("av_jni_set_java_vm 注入成功");
                }
                // MediaCodec 还需要 AppContext，否则报 Both surface and native_window are NULL
                let set_ctx = resolve_avcodec_symbol("av_jni_set_android_app_ctx");
                if set_ctx.is_null() {
                    nlog("av_jni_set_android_app_ctx 未找到：mediacodec 可能仍不可用");
                } else if !appctx.is_null() {
                    let f: JniSetter = std::mem::transmute(set_ctx);
                    f(appctx.cast::<c_void>(), std::ptr::null_mut());
                    nlog("av_jni_set_android_app_ctx 注入成功");
                }
            }
        }
    });
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_init(
    mut env: JNIEnv,
    _class: JClass,
) {
    cache_class(&mut env);
    let _ = with_api(|api| unsafe {
        let handle = match mpv_handle() {
            Ok(h) => h,
            Err(e) => {
                nlog(&format!("init: 句柄不可用 {e}"));
                return;
            }
        };
        let rc = (api.mpv_initialize)(handle);
        if rc < 0 {
            nlog(&format!("init: mpv_initialize 失败 rc={rc}"));
            return;
        }
        G_EVENT_THREAD_EXIT.store(false, Ordering::Release);
        // 把 mpv 自身日志转进 logcat（诊断 vout/解码失败的唯一可靠线索）
        if let Ok(lvl) = CString::new("warn") {
            (api.mpv_request_log_messages)(handle, lvl.as_ptr());
        }
        match std::thread::Builder::new()
            .name("mpv-event".into())
            .spawn(event_thread)
        {
            Ok(_) => nlog("init: 事件线程已拉起"),
            Err(e) => nlog(&format!("init: 事件线程创建失败 {e:?}")),
        }
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
                // MPV_FORMAT_STRING 在 mpv 内部走 `*(char**)data`，
                // 因此这里必须传 **指向 char* 的指针**；直接传 char* 会让 mpv
                // 把字符串头 8 字节当指针解引用（实测 SIGSEGV，fault addr 恰是 "null" 的 ASCII）
                let mut value_ptr: *const c_char = v.as_ptr();
                (api.mpv_set_property)(
                    handle,
                    n.as_ptr(),
                    MPV_FORMAT_STRING,
                    std::ptr::from_mut(&mut value_ptr).cast::<c_void>(),
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
    use std::sync::atomic::AtomicU32;
    static RENDER_CALLS: AtomicU32 = AtomicU32::new(0);
    let n = RENDER_CALLS.fetch_add(1, Ordering::Relaxed);
    if n < 3 {
        nlog(&format!("render_update_callback #{n}"));
    }
    if let Some(VmPtr(vm_ptr)) = G_VM.get() {
        let Ok(vm) = (unsafe { JavaVM::from_raw(vm_ptr.cast()) }) else {
            nlog("render_update_callback: JavaVM 指针无效");
            return;
        };
        let attached = vm.attach_current_thread();
        match attached {
            Ok(mut guard) => {
                let env: &mut JNIEnv = &mut guard;
                call_static(env, "onRenderUpdate", "()V", &[]);
            }
            Err(e) => {
                if n < 3 {
                    nlog(&format!("render_update_callback: attach 失败 {e:?}"));
                }
            }
        }
    }
}

/// 建 render context（幂等）。
///
/// 注意：绝不能在这里 free+重建——vo 持有该上下文指针，单独释放会 use-after-free
/// （实测崩在 `mpv_set_property` 的 strlen）。GL 上下文销毁时必须整体重建 mpv 句柄。
unsafe fn create_render_context(api: &MpvApi) -> c_int {
    let handle = match mpv_handle() {
        Ok(h) => h,
        // 特殊值 -2：mpv 句柄尚未创建（视图早于引擎初始化的正常时序）
        Err(_) => return -2,
    };
    if !G_RENDER_CTX.load(Ordering::Acquire).is_null() {
        return 0;
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
        nlog(&format!("render_context_create 失败 rc={rc}"));
        return rc;
    }
    G_RENDER_CTX.store(ctx, Ordering::Release);
    (api.mpv_render_context_set_update_callback)(
        ctx,
        Some(render_update_callback),
        std::ptr::null_mut(),
    );
    0
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_renderContextCreate(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    // 返回 true 表示 render context 可用；false 时调用方需等待 mpv 就绪后重试
    match with_api(|api| unsafe { create_render_context(api) }) {
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

/// mpv 唤醒后先问一句"真有新帧吗"。
///
/// mpv 的 update 回调会在多种情况下触发（seek、轨道切换、属性变化……），
/// 盲目重绘会画出大量空帧。规范做法是调 mpv_render_context_update() 并检查
/// MPV_RENDER_UPDATE_FRAME 位，只有置位才真正 render。
#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_feature_subject_player_MpvNative_renderContextUpdate(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    let ctx = G_RENDER_CTX.load(Ordering::Acquire);
    if ctx.is_null() {
        return 0;
    }
    match with_api(|api| unsafe { (api.mpv_render_context_update)(ctx) }) {
        Ok(flags) => {
            if flags & MPV_RENDER_UPDATE_FRAME != 0 {
                1
            } else {
                0
            }
        }
        Err(_) => 0,
    }
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
