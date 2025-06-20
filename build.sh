#!/bin/bash

# 构建脚本 - build.sh
# 用于编译和打包proxy-service项目

echo "开始构建proxy-service项目..."

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java环境，请安装Java 17或更高版本"
    exit 1
fi

# 检查Maven环境
if ! command -v mvn &> /dev/null; then
    echo "错误: 未找到Maven环境，请安装Maven 3.6或更高版本"
    exit 1
fi

# 清理之前的构建
echo "清理之前的构建..."
mvn clean

# 编译项目
echo "编译项目..."
mvn compile

if [ $? -ne 0 ]; then
    echo "错误: 编译失败"
    exit 1
fi

# 运行测试
echo "运行测试..."
mvn test

if [ $? -ne 0 ]; then
    echo "警告: 测试失败，但继续构建"
fi

# 打包项目
echo "打包项目..."
mvn package -DskipTests

if [ $? -ne 0 ]; then
    echo "错误: 打包失败"
    exit 1
fi

echo "构建完成！"
echo "生成的JAR文件: target/proxy-service-1.0.0.jar"
echo ""
echo "运行应用:"
echo "java -jar target/proxy-service-1.0.0.jar"
echo ""
echo "或使用Maven运行:"
echo "mvn spring-boot:run"

