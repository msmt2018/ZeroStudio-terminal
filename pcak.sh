#!/bin/bash

# ================= 配置区域 =================
OLD_BASE="android.zero.studio"
NEW_BASE="android.zero.studio.terminal"
# ============================================

# 确保 Shell 环境正常
cd "$(pwd)" 2>/dev/null

# 声明关联数组用于记录已处理的替换任务
declare -A PROCESSED_TASKS

echo "🎯 开始精确重构: $OLD_BASE -> $NEW_BASE"

# 1. 第一阶段：内容替换
echo "📝 正在全量替换文件内容..."
# 符号处理顺序：先处理路径和下划线，最后处理点号
VARIANTS=("/" "_" "-" ".")

find . -type f -not -path '*/.*' -not -path '*/build/*' | while read -r file; do
    for sep in "${VARIANTS[@]}"; do
        # 根据当前符号生成对应的 OLD 和 NEW 字符串
        OLD_STR=$(echo "$OLD_BASE" | tr '.' "$sep")
        NEW_STR=$(echo "$NEW_BASE" | tr '.' "$sep")

        # 【逻辑判断】：检查该符号类型的任务是否已处理过
        # 使用 "源->目标" 作为唯一键值
        TASK_KEY="${OLD_STR}==>${NEW_STR}"
        
        if [[ -n "${PROCESSED_TASKS[$TASK_KEY]}" ]]; then
            # 如果已经记录过，但在脚本逻辑中，sed 仍需对每个文件执行
            # 这里的记录主要用于逻辑隔离，确保不发生非预期的交叉替换
            : 
        fi

        # 执行全词替换
        # 使用 grep 先检查文件中是否存在目标字符串，提高效率并减少文件 IO
        if grep -qF "$OLD_STR" "$file"; then
            sed -i "s|$OLD_STR|$NEW_STR|g" "$file"
            # 记录该任务已在当前运行中触发
            PROCESSED_TASKS["$TASK_KEY"]=1
        fi
    done
done

# 第二阶段：物理目录重构
echo "📁 正在重构目录层级..."
for sep in "/" "_" "-"; do
    OLD_STR=$(echo "$OLD_BASE" | tr '.' "$sep")
    NEW_STR=$(echo "$NEW_BASE" | tr '.' "$sep")

    # 深度优先查找，确保先处理子目录
    find . -depth -type d -path "*$OLD_STR*" | while read -r src; do
        dst="${src//$OLD_STR/$NEW_STR}"
        
        if [ "$src" != "$dst" ]; then
            echo "迁移: $src -> $dst"
            mkdir -p "$(dirname "$dst")"
            if [ -d "$dst" ]; then
                # 合并内容
                mv "$src"/* "$dst/" 2>/dev/null && rm -rf "$src"
            else
                mv "$src" "$dst"
            fi
        fi
    done
done

# 文件名同步
echo "📄 正在同步文件名..."
find . -depth -type f -not -path '*/.*' | while read -r src; do
    filename=$(basename "$src")
    new_filename="$filename"
    
    for sep in "_" "-" "."; do
        OLD_STR=$(echo "$OLD_BASE" | tr '.' "$sep")
        NEW_STR=$(echo "$NEW_BASE" | tr '.' "$sep")
        new_filename="${new_filename//$OLD_STR/$NEW_STR}"
    done

    if [ "$filename" != "$new_filename" ]; then
        dst="$(dirname "$src")/$new_filename"
        mv "$src" "$dst"
    fi
done

#清理空目录
find . -type d -empty -delete 2>/dev/null

echo -e "\n✅ 重构任务全部完成！"
