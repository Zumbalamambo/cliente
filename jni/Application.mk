#APP_ABI := all
#APP_ABI := x86
#XXX: Vuforia solo esta disponible para plataforma ARM y no para x86 ni mips, con lo cual solo podemos compilar para ARM
APP_ABI := armeabi-v7a
APP_OPTIM := debug # para incluir simbolos para debuggear
NDK_DEBUG := 1

# http://www.kandroid.org/ndk/docs/CPLUSPLUS-SUPPORT.html
# http://stackoverflow.com/questions/20557910/android-ndk-and-eclipse-give-different-error-info-about-a-c-getline-function
#APP_STL := gnustl_static
APP_STL := gnustl_shared
#APP_STL := stlport_static  # con esto crashea inmediatamente con lo de opencv read from xml cargando el archivo de calibracion de la camar
#APP_STL := stlport_shared
#APP_STL := libc++_static # me dice invalida usando ndk-r9
#APP_STL := libc++_shared
#The following choices do not include std::ifstream:
#APP_STL := gabi++_static # opencv no compila con esta opcion
#APP_STL := gabi++_shared
#APP_STL := system
# Note that libc++_ were not available before NDK r9, and are not documented well as of today.
APP_CPPFLAGS := -frtti -fexceptions -Wno-error=format-security -g3 #-fpermissive
APP_PLATFORM := android-14
#APP_OPTIM := debug # para debugging y testing nada mas
