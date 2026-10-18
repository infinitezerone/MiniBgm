//! On Android, rustls-platform-verifier is patched to use webpki-root-certs natively
//! without requiring JVM JNI initialization. Stub functions are preserved for API compatibility.

use jni::errors::Error as JNIError;
use jni::objects::{Global, JClassLoader, JObject};
use jni::JavaVM;

/// A layer to access the Android runtime.
pub trait Runtime: Send + Sync {
    /// Returns a handle to the current process' JVM.
    fn java_vm(&self) -> &JavaVM;
    /// Returns a reference to the current app's Context.
    fn context(&self) -> &Global<JObject<'static>>;
    /// Returns a reference to the class returned by the current JVM's `getClassLoader` call.
    fn class_loader(&self) -> &Global<JClassLoader<'static>>;
}

/// Initialize given a typical Android NDK Env and JObject context.
pub fn init_with_env(_env: &mut jni::Env, _context: JObject) -> Result<(), JNIError> {
    Ok(())
}

/// Initialize with a runtime that can dynamically serve references to
/// the JVM, context, and class loader.
pub fn init_with_runtime(_runtime: &'static dyn Runtime) {}

/// Initialize with references to the JVM, context, and class loader.
pub fn init_with_refs(
    _java_vm: JavaVM,
    _context: Global<JObject<'static>>,
    _loader: Global<JClassLoader<'static>>,
) {}
