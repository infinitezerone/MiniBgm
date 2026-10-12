package com.infinitezerone.minibgm.torrent

import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecOperations
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

private const val JNI_LIBS_PATH = "torrent-native/jniLibs"
private const val STAMP_FILE_NAME = ".native-torrent-build.stamp"
private const val CRATE_NAME = "minibgm-torrent"
private const val MIN_PLATFORM_API = 24
private const val SKIP_BUILD_PROPERTY = "minibgm.torrent.skipBuild"
private const val NDK_DIR_PROPERTY = "minibgm.torrent.ndkDir"

private val SUPPORTED_ABIS = listOf("arm64-v8a", "x86_64")

class TorrentNativePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val jniLibsDirectory = target.layout.buildDirectory.dir(JNI_LIBS_PATH)
        val projectDirectory = target.layout.projectDirectory
        val buildTorrentNative =
            target.tasks.register<BuildTorrentNativeTask>("buildTorrentNative") {
                group = "build"
                description = "编译 Rust BT/磁力流播原生库 (libminibgm_torrent.so) 并校验产物完整"
                workspaceDir.set(target.rootProject.layout.projectDirectory)
                localPropertiesFile.set(target.rootProject.layout.projectDirectory.file("local.properties"))
                rustSources.from(rustSourceFiles(target))
                jniLibsDir.set(jniLibsDirectory)
                this.projectDir.set(projectDirectory)
                abis.convention(SUPPORTED_ABIS)
                platformApi.convention(MIN_PLATFORM_API)
                skipBuild.convention(
                    target.providers.gradleProperty(SKIP_BUILD_PROPERTY).map(String::toBoolean).orElse(false),
                )
                ndkDirFromProperty.convention(target.providers.gradleProperty(NDK_DIR_PROPERTY))
                ndkDirFromEnv.convention(ndkDirFromEnvironment(target))
                sdkDirFromEnv.convention(
                    target.providers
                        .environmentVariable("ANDROID_HOME")
                        .orElse(target.providers.environmentVariable("ANDROID_SDK_ROOT")),
                )
            }

        target.extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
            onVariants { variant ->
                val jniLibs =
                    variant.sources.jniLibs
                        ?: throw GradleException(
                            "❌ [torrent-native] AGP 未给 variant ${variant.name} 提供 jniLibs 源目录，" +
                                "无法把 libminibgm_torrent.so 接进打包链路。",
                        )
                jniLibs.addStaticSourceDirectory(jniLibsDirectory.get().asFile.absolutePath)
            }
        }

        target.tasks.configureEach {
            if (name.endsWith("JniLibFolders") || name.endsWith("NativeLibs")) {
                dependsOn(buildTorrentNative)
            }
        }
    }
}

private fun rustSourceFiles(target: Project): ConfigurableFileCollection =
    target.files(
        target.rootProject.fileTree("crates/minibgm-torrent") {
            include("**/*.rs", "**/Cargo.toml", "**/Cargo.lock")
            exclude("**/target/**")
        },
        target.rootProject.file("Cargo.toml"),
        target.rootProject.file("Cargo.lock"),
    )

private fun ndkDirFromEnvironment(target: Project): Provider<String> =
    target.providers
        .environmentVariable("ANDROID_NDK_HOME")
        .orElse(target.providers.environmentVariable("ANDROID_NDK_ROOT"))
        .map { it.trim() }

abstract class BuildTorrentNativeTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rustSources: ConfigurableFileCollection

    @get:Input
    abstract val abis: ListProperty<String>

    @get:Input
    abstract val platformApi: Property<Int>

    @get:Input
    abstract val skipBuild: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val ndkDirFromProperty: Property<String>

    @get:Input
    @get:Optional
    abstract val ndkDirFromEnv: Property<String>

    @get:Input
    @get:Optional
    abstract val sdkDirFromEnv: Property<String>

    @get:OutputDirectory
    abstract val jniLibsDir: DirectoryProperty

    @get:Internal
    abstract val workspaceDir: DirectoryProperty

    @get:Internal
    abstract val localPropertiesFile: RegularFileProperty

    @get:Internal
    abstract val projectDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun buildNative() {
        val outputDir = jniLibsDir.get().asFile
        val expectedAbis = abis.get()
        val fingerprint = rustFingerprint()

        if (skipBuild.get()) {
            verifyAbiArtifacts(outputDir, expectedAbis)
            verifyFingerprint(outputDir, fingerprint)
            logger.lifecycle("[torrent-native] 跳过编译，已有产物与 Rust 源码指纹一致")
            return
        }

        val ndkDir =
            resolveNdkDir()
                ?: throw GradleException("❌ [torrent-native] 未找到可用的 Android NDK，无法编译 libminibgm_torrent.so。")
        val cargo =
            findExecutable("cargo")
                ?: throw GradleException("❌ [torrent-native] 未找到 cargo，无法编译 libminibgm_torrent.so。")
        findExecutable("cargo-ndk")
            ?: throw GradleException("❌ [torrent-native] 未找到 cargo-ndk，无法交叉编译 Android 产物。")

        outputDir.mkdirs()
        logger.lifecycle(
            "[torrent-native] cargo-ndk 编译 ${expectedAbis.joinToString()}（NDK: ${ndkDir.absolutePath}）...",
        )
        execOperations
            .exec {
                workingDir(workspaceDir.get().asFile)
                val command =
                    buildList {
                        add(cargo.absolutePath)
                        add("ndk")
                        expectedAbis.forEach { abi ->
                            add("-t")
                            add(abi)
                        }
                        add("--platform")
                        add(platformApi.get().toString())
                        add("-o")
                        add(outputDir.absolutePath)
                        add("build")
                        add("--release")
                        add("-p")
                        add(CRATE_NAME)
                    }
                commandLine(command)
                environment("ANDROID_NDK_HOME", ndkDir.absolutePath)
                environment("ANDROID_NDK_ROOT", ndkDir.absolutePath)
                val cltDir = File("/Library/Developer/CommandLineTools")
                if (cltDir.isDirectory) {
                    environment("DEVELOPER_DIR", cltDir.absolutePath)
                }
            }.assertNormalExitValue()

        verifyAbiArtifacts(outputDir, expectedAbis)
        writeFingerprint(outputDir, fingerprint)
    }

    private fun rustFingerprint(): String {
        val root = workspaceDir.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        rustSources.files
            .filter { it.isFile }
            .sortedBy { it.absolutePath }
            .forEach { file ->
                digest.update(file.relativeToOrSelf(root).invariantSeparatorsPath.toByteArray())
                digest.update(0)
                digest.update(file.readBytes())
            }
        abis.get().sorted().forEach { digest.update(it.toByteArray()) }
        digest.update(platformApi.get().toString().toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun verifyAbiArtifacts(
        outputDir: File,
        expectedAbis: List<String>,
    ) {
        val missing =
            expectedAbis.filter { abi ->
                val so = File(outputDir, "$abi/libminibgm_torrent.so")
                !so.exists() || so.length() == 0L
            }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "❌ [torrent-native] 缺少原生库产物：${missing.joinToString()}（目录 ${outputDir.absolutePath}）。",
            )
        }
    }

    private fun verifyFingerprint(
        outputDir: File,
        fingerprint: String,
    ) {
        val stamp = File(outputDir, STAMP_FILE_NAME)
        val recorded = runCatching { stamp.readText().trim() }.getOrNull()
        if (recorded != fingerprint) {
            throw GradleException(
                "❌ [torrent-native] 已有 .so 与当前 Rust 源码不一致（产物过期），但已要求跳过编译。",
            )
        }
    }

    private fun writeFingerprint(
        outputDir: File,
        fingerprint: String,
    ) {
        File(outputDir, STAMP_FILE_NAME).writeText("$fingerprint\n")
    }

    private fun resolveNdkDir(): File? {
        ndkDirFromProperty
            .orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { return File(it).takeIf { dir -> dir.isDirectory } }
        ndkDirFromEnv
            .orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { return File(it).takeIf { dir -> dir.isDirectory } }
        return candidateNdkDirs()
            ?.filter(::isUsableNdk)
            ?.maxByOrNull { ndkSortKey(it.name) }
    }

    private fun isUsableNdk(directory: File): Boolean =
        File(directory, "source.properties").isFile &&
            File(directory, "toolchains").isDirectory

    private fun candidateNdkDirs(): List<File>? {
        val sdkDir = resolveSdkDir() ?: return null
        val ndkParent = File(sdkDir, "ndk").takeIf { it.isDirectory } ?: return null
        return ndkParent.listFiles()?.filter { it.isDirectory }
    }

    private fun resolveSdkDir(): File? {
        sdkDirFromEnv
            .orNull
            ?.takeIf { it.isNotBlank() }
            ?.let { return File(it).takeIf { dir -> dir.isDirectory } }
        val propsFile = localPropertiesFile.asFile.get()
        if (propsFile.isFile) {
            val props = java.util.Properties()
            propsFile.inputStream().use { props.load(it) }
            props.getProperty("sdk.dir")?.let { return File(it).takeIf { dir -> dir.isDirectory } }
        }
        return null
    }

    private fun findExecutable(name: String): File? {
        val pathEntries = System.getenv("PATH")?.split(File.pathSeparator).orEmpty()
        val standardLocations =
            listOf(
                File(System.getProperty("user.home"), ".cargo/bin"),
                File("/opt/homebrew/bin"),
                File("/usr/local/bin"),
                File("/usr/bin"),
            )
        val allDirs = (pathEntries.map { File(it) } + standardLocations).distinct()
        val suffixes =
            if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
                listOf(".exe", ".cmd", ".bat", "")
            } else {
                listOf("")
            }
        return allDirs
            .asSequence()
            .flatMap { directory -> suffixes.asSequence().map { suffix -> File(directory, name + suffix) } }
            .firstOrNull { it.isFile }
    }
}

private fun ndkSortKey(version: String): String = version.split('.').joinToString(".") { it.padStart(6, '0') }

