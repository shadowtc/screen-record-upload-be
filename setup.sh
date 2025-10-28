#!/bin/bash

# Setup script for MinIO Multipart Upload Demo

echo "Setting up MinIO Multipart Upload Demo..."

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Java is not installed. Please install Java 17 or higher."
    echo "sudo apt-get install openjdk-17-jdk"
    exit 1
fi

# Download and setup Maven if not installed
if ! command -v mvn &> /dev/null; then
    echo "Maven not found. Downloading Maven..."
    MAVEN_VERSION=3.9.5
    wget https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz
    tar -xzf apache-maven-${MAVEN_VERSION}-bin.tar.gz
    export PATH=$PWD/apache-maven-${MAVEN_VERSION}/bin:$PATH
    echo "Maven downloaded and configured"
fi

# Build the project
echo "Building the project..."
mvn clean package -DskipTests

echo "Setup complete!"
echo ""
echo "To run the application:"
echo "  mvn spring-boot:run"
echo ""
echo "Or:"
echo "  java -jar target/minio-multipart-upload-1.0.0.jar"
