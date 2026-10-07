# ===== Cloud Studio 一键部署脚本 =====
# 在 Cloud Studio 终端里执行：bash deploy.sh

echo "=========================================="
echo "  智学选课系统 — Cloud Studio 部署脚本"
echo "=========================================="

# 1. 检查 JDK
if ! command -v java &> /dev/null; then
    echo "📦 安装 JDK 17 ..."
    sudo apt update && sudo apt install -y openjdk-17-jdk maven
fi
java -version 2>&1 | head -1

# 2. 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "📦 安装 Docker ..."
    curl -fsSL https://get.docker.com | sh
    sudo usermod -aG docker $USER
fi
docker --version

# 3. 启动 MySQL 容器（如果没在跑）
echo "🐳 启动 MySQL 8 容器 ..."
docker ps | grep zhixue-mysql > /dev/null 2>&1 || docker run -d \
    --name zhixue-mysql \
    -e MYSQL_ROOT_PASSWORD=zhixue123 \
    -e MYSQL_DATABASE=zhixue \
    -p 3306:3306 \
    -v zhixue-mysql-data:/var/lib/mysql \
    mysql:8 \
    --character-set-server=utf8mb4 \
    --collation-server=utf8mb4_unicode_ci

# 等 MySQL 就绪
echo "⏳ 等待 MySQL 启动 ..."
for i in $(seq 1 30); do
    if docker exec zhixue-mysql mysql -uroot -pzhixue123 -e "SELECT 1" > /dev/null 2>&1; then
        echo "✅ MySQL 就绪！"
        break
    fi
    sleep 2
done

# 4. 编译打包
echo "🔨 Maven 打包 ..."
mvn clean package -DskipTests -q
ls -lh target/zhixue-selection-*.jar

# 5. 启动 Spring Boot
echo "🚀 启动 Spring Boot ..."
JAR=$(ls target/zhixue-selection-*.jar | head -1)
nohup java -jar "$JAR" \
    --server.port=8080 \
    --spring.profiles.active=cloudstudio \
    > app.log 2>&1 &

echo ""
echo "=========================================="
echo "  ✅ 部署完成！"
echo "=========================================="
echo "  📱 访问地址：看 Cloud Studio 端口转发 8080"
echo "  🔑 默认账号："
echo "     学生：2023010101 / 123456"
echo "     教师：T001 / 123456"
echo "     管理员：admin / admin"
echo "  📋 日志：tail -f app.log"
echo "  🛑 停止：kill \$(pgrep -f zhixue-selection)"
echo "=========================================="
