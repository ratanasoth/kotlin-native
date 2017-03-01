package org.jetbrains.kotlin.backend.konan

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.backend.konan.KonanPlatform
import org.jetbrains.kotlin.backend.konan.util.profile
import org.jetbrains.kotlin.backend.konan.Distribution
import org.jetbrains.kotlin.backend.konan.llvm.loadMetadata
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

class KonanConfig(val project: Project, val configuration: CompilerConfiguration) {

    internal val distribution = Distribution(configuration)

    internal val compileAsCore = configuration.get(KonanConfigKeys.COMPILE_AS_CORE) ?: false

    internal val libraries: List<String>
        get() {
            val result = mutableListOf<String>()

            val fromCommandLine = configuration.getList(KonanConfigKeys.LIBRARY_FILES)
            result.addAll(fromCommandLine)

            if (!(configuration.get(KonanConfigKeys.NOSTDLIB) ?: false)) result.add(distribution.stdlib)
            if (!(configuration.get(KonanConfigKeys.NOCORE) ?: false)) result.add(distribution.core)

            println("### libraries = $result")

            return result
        }

    private val loadedDescriptors = loadLibMetadata(libraries)

    init {
        if (!compileAsCore) {
            val core = loadedDescriptors.single { it.isCore() }
            KonanPlatform.builtIns.createBuiltInsModule(core)
        }
    }

    internal val librariesToLink: List<String>
        get() = libraries + configuration.getList(KonanConfigKeys.NATIVE_LIBRARY_FILES)

    val moduleId: String
        get() = configuration.getNotNull(CommonConfigurationKeys.MODULE_NAME)

    fun loadLibMetadata(libraries: List<String>): List<ModuleDescriptorImpl> {

        val allMetadata = mutableListOf<ModuleDescriptorImpl>()

        for (path in libraries) {
            val filePath = File(path)
            if (!filePath.exists()) {
                error("Path '" + path + "' does not exist")
            }

            profile("Loading ${filePath}") {
                val moduleDescriptor = loadMetadata(configuration, filePath)
                allMetadata.add(moduleDescriptor)
            }
        }
            println("### libraries = $allMetadata")
        return allMetadata
    }

    internal val moduleDescriptors: List<ModuleDescriptorImpl> by lazy {
        for (module in loadedDescriptors) {
            // Yes, just to all of them.
            setDependencies(module, loadedDescriptors)
        }

        loadedDescriptors 
    }

    companion object {
        private fun setDependencies(module: ModuleDescriptorImpl, modules: List<ModuleDescriptorImpl>) {
            module.setDependencies(modules.plus(KonanPlatform.builtIns.builtInsModule))
        }
    }

}
