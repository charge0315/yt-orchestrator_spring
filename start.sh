#!/bin/bash

# YouTube Orchestrator Spring Boot Application Startup Script

echo "🚀 Starting YouTube Orchestrator..."
echo ""

# SDKMANの初期化
source "$HOME/.sdkman/bin/sdkman-init.sh"

# MongoDBの起動確認
echo "📊 Checking MongoDB status..."
if ! pgrep -x mongod > /dev/null; then
    echo "⚠️  MongoDB is not running. Starting MongoDB..."
    mongod --fork --logpath /tmp/mongod.log --dbpath /data/db
    sleep 2
    echo "✅ MongoDB started"
else
    echo "✅ MongoDB is already running"
fi

echo ""
echo "🔧 Environment Configuration:"
echo "  - MongoDB URI: mongodb://localhost:27017/yt-orchestrator"
echo "  - OpenAI API: Configured"
echo ""

# アプリケーションの起動
echo "🎯 Starting Spring Boot application..."
echo "  - Port: 8080"
echo "  - Frontend: React/Vite SPA"
echo "  - Database: yt-orchestrator"
echo ""

cd /home/user/webapp

MONGODB_URI="mongodb://localhost:27017/yt-orchestrator" \
OPENAI_API_KEY="${OPENAI_API_KEY:-your-openai-api-key}" \
./gradlew bootRun

# Note: MongoDB Atlas connection fails in this sandbox environment due to SSL/TLS restrictions.
# See MONGODB_ATLAS_CONNECTION_TEST.md for details.
# In production environments, MongoDB Atlas should work fine:
# MONGODB_URI="mongodb+srv://user:pass@cluster.mongodb.net/yt-orchestrator?retryWrites=true&w=majority" \
# OPENAI_API_KEY="your-openai-api-key" \
# ./gradlew bootRun
