#!/bin/bash

# Define base path and package
BASE_PATH="./app/src/main/java/com/fatdogs/verifylabs"

echo "📁 Creating Clean Architecture folders under $BASE_PATH"

# Presentation Layer
mkdir -p $BASE_PATH/presentation/view
mkdir -p $BASE_PATH/presentation/viewmodel
mkdir -p $BASE_PATH/presentation/di

# Domain Layer
mkdir -p $BASE_PATH/domain/model
mkdir -p $BASE_PATH/domain/usecase
mkdir -p $BASE_PATH/domain/repository

# Data Layer
mkdir -p $BASE_PATH/data/remote
mkdir -p $BASE_PATH/data/local
mkdir -p $BASE_PATH/data/repository

# Core/Utils
mkdir -p $BASE_PATH/core/util

# Optional Sample File (You can remove if not needed)
echo "package com.fatdogs.verifylabs.domain.model

data class SampleModel(
    val id: Int,
    val name: String
)
" > $BASE_PATH/domain/model/SampleModel.kt

echo "✅ Clean Architecture folders successfully created!"

