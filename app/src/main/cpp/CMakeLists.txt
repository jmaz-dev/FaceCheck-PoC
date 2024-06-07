
cmake_minimum_required(VERSION 3.10.2)

project(com.example.facecheckpoc LANGUAGES CXX)

# Adiciona os arquivos de código fonte C++
add_library(native-lib SHARED
        native-lib.cpp
        # Outros arquivos .cpp da sua biblioteca
)

# Encontra a biblioteca de log do Android
find_library(log-lib log)

# Adiciona as bibliotecas Lottie e TensorFlow Lite (ajuste os caminhos se necessário)
#find_library(lottie-lib Lottie)
#find_library(tflite-lib tensorflowlite)

# Linka as bibliotecas à sua biblioteca nativa
target_link_libraries( # Specifies the target library.
        native-lib

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})