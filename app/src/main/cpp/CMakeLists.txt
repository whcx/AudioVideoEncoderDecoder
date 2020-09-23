cmake_minimum_required(VERSION 3.4.1)

project(av-lib)

set(CMAKE_VERBOSE_MAKEFILE on)
message("native audio video lib compile begin...")
message(STATUS ${CMAKE_SOURCE_DIR})
message(STATUS ${PROJECT_SOURCE_DIR})

set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${PROJECT_SOURCE_DIR}/../lib)
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${PROJECT_SOURCE_DIR}/../lib)

aux_source_directory(. SRCS)

add_library( # Sets the name of the library.
             av-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             ${SRCS})

#note 使用target_include_directories需要指定target即av-lib,所以需要把这行放在add_library之后，否则编译报错，提示不能为target指定include目录，因为target还没built。
target_include_directories(av-lib PUBLIC ${CMAKE_SOURCE_DIR}/include)

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

target_link_libraries( # Specifies the target library.
                       av-lib

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )