package dev.stashy.standard.samples.gallery

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@Module
@ComponentScan
internal class GalleryModule

@KoinApplication(modules = [GalleryModule::class])
internal class GalleryApp
