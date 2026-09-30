mod client;

use std::sync::OnceLock;
use client::EchHttpClient;
use jni::objects::{JByteArray, JClass, JObjectArray, JString};
use jni::sys::{jboolean, jint, jlong, jobject};
use jni::JNIEnv;

static CLIENT: OnceLock<EchHttpClient> = OnceLock::new();

fn get_client() -> Result<&'static EchHttpClient, String> {
    CLIENT.get_or_init(|| {
        EchHttpClient::new().expect("Failed to initialize EchHttpClient")
    });
    CLIENT.get().ok_or_else(|| "EchHttpClient not initialized".to_string())
}

#[no_mangle]
pub extern "system" fn Java_com_infinitezerone_minibgm_core_network_ech_EchNativeClient_nativeFetch(
    mut env: JNIEnv,
    _class: JClass,
    j_url: JString,
    j_method: JString,
    j_header_keys: JObjectArray,
    j_header_values: JObjectArray,
    j_body: JByteArray,
    j_timeout_ms: jlong,
    j_target_addrs: JObjectArray,
    j_enable_ech: jboolean,
) -> jobject {
    let result = (|| -> Result<jobject, Box<dyn std::error::Error>> {
        let url: String = env.get_string(&j_url)?.into();
        let method: String = env.get_string(&j_method)?.into();

        // 提取 Headers
        let keys_len = env.get_array_length(&j_header_keys)?;
        let mut headers = Vec::with_capacity(keys_len as usize);
        for i in 0..keys_len {
            let key_obj: JString = env.get_object_array_element(&j_header_keys, i)?.into();
            let val_obj: JString = env.get_object_array_element(&j_header_values, i)?.into();
            let key: String = env.get_string(&key_obj)?.into();
            let val: String = env.get_string(&val_obj)?.into();
            headers.push((key, val));
        }

        // 提取 Body
        let body = if !j_body.is_null() {
            let body_vec = env.convert_byte_array(&j_body)?;
            Some(body_vec)
        } else {
            None
        };

        // 提取可选目标地址列表（用于抗 DNS 污染直连或测试）
        let target_addrs = if !j_target_addrs.is_null() {
            let addrs_len = env.get_array_length(&j_target_addrs)?;
            let mut list = Vec::with_capacity(addrs_len as usize);
            for i in 0..addrs_len {
                let elem: JString = env.get_object_array_element(&j_target_addrs, i)?.into();
                let s: String = env.get_string(&elem)?.into();
                let trimmed = s.trim().to_string();
                if !trimmed.is_empty() {
                    list.push(trimmed);
                }
            }
            if list.is_empty() {
                None
            } else {
                Some(list)
            }
        } else {
            None
        };

        let enable_ech = j_enable_ech != 0;

        let client = get_client().map_err(|e| e.to_string())?;
        let resp = client
            .fetch(
                &url,
                &method,
                &headers,
                body.as_deref(),
                j_timeout_ms as u64,
                target_addrs.as_deref(),
                enable_ech,
            )
            .map_err(|e| e.to_string())?;

        // 构造返回给 Java 的 EchNativeResponse 对象
        let resp_class = env.find_class("com/infinitezerone/minibgm/core/network/ech/EchNativeResponse")?;

        // 转换 headerKeys 和 headerValues
        let string_class = env.find_class("java/lang/String")?;
        let resp_keys_arr = env.new_object_array(resp.headers.len() as i32, &string_class, JString::default())?;
        let resp_vals_arr = env.new_object_array(resp.headers.len() as i32, &string_class, JString::default())?;

        for (idx, (k, v)) in resp.headers.iter().enumerate() {
            let k_jstr = env.new_string(k)?;
            let v_jstr = env.new_string(v)?;
            env.set_object_array_element(&resp_keys_arr, idx as i32, k_jstr)?;
            env.set_object_array_element(&resp_vals_arr, idx as i32, v_jstr)?;
        }

        // 转换 body
        let body_arr = env.byte_array_from_slice(&resp.body)?;

        // 构造对象: EchNativeResponse(statusCode, headerKeys, headerValues, body, echAccepted, errorMessage)
        let ctor_sig = "(I[Ljava/lang/String;[Ljava/lang/String;[BZLjava/lang/String;)V";
        let null_err_msg = JString::default();
        let obj = env.new_object(
            resp_class,
            ctor_sig,
            &[
                (resp.status_code as jint).into(),
                (&resp_keys_arr).into(),
                (&resp_vals_arr).into(),
                (&body_arr).into(),
                (resp.ech_accepted as jboolean).into(),
                (&null_err_msg).into(),
            ],
        )?;

        Ok(obj.into_raw())
    })();

    match result {
        Ok(obj) => obj,
        Err(e) => {
            // 失败时构造带 errorMessage 的错误响应
            if let Ok(resp_class) = env.find_class("com/infinitezerone/minibgm/core/network/ech/EchNativeResponse") {
                let err_str = env.new_string(e.to_string()).unwrap_or_default();
                let string_class = env.find_class("java/lang/String").unwrap();
                let empty_keys = env.new_object_array(0, &string_class, JString::default()).unwrap();
                let empty_vals = env.new_object_array(0, &string_class, JString::default()).unwrap();
                let empty_body = env.byte_array_from_slice(&[]).unwrap();

                let ctor_sig = "(I[Ljava/lang/String;[Ljava/lang/String;[BZLjava/lang/String;)V";
                if let Ok(err_obj) = env.new_object(
                    resp_class,
                    ctor_sig,
                    &[
                        (0 as jint).into(),
                        (&empty_keys).into(),
                        (&empty_vals).into(),
                        (&empty_body).into(),
                        (false as jboolean).into(),
                        (&err_str).into(),
                    ],
                ) {
                    return err_obj.into_raw();
                }
            }
            std::ptr::null_mut()
        }
    }
}
