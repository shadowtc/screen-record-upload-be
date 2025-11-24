#!/bin/bash

# LibreOffice Installation Script
# Supports Ubuntu/Debian, CentOS/RHEL, and macOS

set -e

echo "======================================"
echo "LibreOffice Installation Script"
echo "======================================"
echo ""

# Detect OS
if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS=$ID
    VERSION=$VERSION_ID
elif type lsb_release >/dev/null 2>&1; then
    OS=$(lsb_release -si | tr '[:upper:]' '[:lower:]')
elif [ -f /etc/lsb-release ]; then
    . /etc/lsb-release
    OS=$(echo $DISTRIB_ID | tr '[:upper:]' '[:lower:]')
elif [ "$(uname)" == "Darwin" ]; then
    OS="macos"
else
    OS=$(uname -s)
fi

echo "Detected OS: $OS"
echo ""

# Check if LibreOffice is already installed
if command -v soffice &> /dev/null || command -v libreoffice &> /dev/null; then
    echo "LibreOffice is already installed!"
    echo ""
    
    # Try to get version
    if command -v soffice &> /dev/null; then
        soffice --version
    elif command -v libreoffice &> /dev/null; then
        libreoffice --version
    fi
    
    echo ""
    echo "Installation path:"
    which soffice 2>/dev/null || which libreoffice 2>/dev/null || echo "Command not found in PATH"
    
    echo ""
    read -p "LibreOffice is already installed. Reinstall? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Installation cancelled."
        exit 0
    fi
fi

echo ""
echo "Installing LibreOffice..."
echo ""

case "$OS" in
    ubuntu|debian|linuxmint)
        echo "Installing on Debian/Ubuntu..."
        sudo apt-get update
        sudo apt-get install -y libreoffice
        ;;
    
    centos|rhel|fedora)
        echo "Installing on CentOS/RHEL/Fedora..."
        sudo yum install -y libreoffice
        ;;
    
    macos)
        echo "Installing on macOS..."
        if ! command -v brew &> /dev/null; then
            echo "Error: Homebrew is not installed."
            echo "Please install Homebrew first: https://brew.sh/"
            exit 1
        fi
        brew install --cask libreoffice
        ;;
    
    *)
        echo "Unsupported OS: $OS"
        echo ""
        echo "Please install LibreOffice manually:"
        echo "  - Ubuntu/Debian: sudo apt-get install -y libreoffice"
        echo "  - CentOS/RHEL: sudo yum install -y libreoffice"
        echo "  - macOS: brew install --cask libreoffice"
        echo "  - Other: https://www.libreoffice.org/download/"
        exit 1
        ;;
esac

echo ""
echo "======================================"
echo "Installation Complete!"
echo "======================================"
echo ""

# Verify installation
if command -v soffice &> /dev/null; then
    echo "✓ LibreOffice installed successfully"
    echo ""
    soffice --version
    echo ""
    echo "Installation path: $(which soffice)"
elif command -v libreoffice &> /dev/null; then
    echo "✓ LibreOffice installed successfully"
    echo ""
    libreoffice --version
    echo ""
    echo "Installation path: $(which libreoffice)"
else
    echo "✗ LibreOffice installation may have failed"
    echo ""
    echo "Please verify manually by running:"
    echo "  soffice --version"
    exit 1
fi

echo ""
echo "You can now use LibreOffice for Word to PDF conversion!"
echo ""
echo "To test the conversion:"
echo "  ./test-word-conversion.sh your-document.docx"
