package com.ct.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReadTxt2JsonUtils {

    /**
     * 转换指定目录中的所有txt文件为json文件（带动画名称递增）
     *
     * @param sourceDirPath  源目录路径（包含txt文件）
     * @param targetDirPath  目标目录路径（存放json文件）
     * @param normalizeName  是否重命名文件
     * @param baseAnimationName 基础动画名称（如"aqq2_eff"）
     * @param frameRate      帧率（如16）
     * @throws IOException  如果发生I/O错误
     */
    public static void convertTxtToJson(String sourceDirPath, String targetDirPath,
                                        boolean normalizeName, String baseAnimationName,
                                        int frameRate) throws IOException {
        Path sourceDir = Paths.get(sourceDirPath);
        Path targetDir = Paths.get(targetDirPath);

        // 验证源目录是否存在
        if (!Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("源目录不存在: " + sourceDirPath);
        }

        // 创建目标目录（如果不存在）
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        // 收集所有txt文件
        List<Path> txtFiles = new ArrayList<>();
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().toLowerCase().endsWith(".txt")) {
                    txtFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        // 按文件名排序（确保处理顺序一致）
        Collections.sort(txtFiles);

        // 解析基础动画名称中的数字部分
        Pattern pattern = Pattern.compile("(.*?)(\\d+)(.*)");
        Matcher matcher = pattern.matcher(baseAnimationName);

        String prefix = "";
        String suffix = "";
        int startIndex = 0;

        if (matcher.find()) {
            prefix = matcher.group(1);
            startIndex = Integer.parseInt(matcher.group(2));
            suffix = matcher.group(3);
        } else {
            // 如果没有数字，直接使用原始名称
            prefix = baseAnimationName;
        }

        // 处理每个文件
        for (int i = 0; i < txtFiles.size(); i++) {
            Path txtFile = txtFiles.get(i);

            // 生成递增的动画名称
            String currentAnimationName = prefix + (startIndex + i) + suffix;

            processTxtFile(txtFile, sourceDir, targetDir, normalizeName,
                    currentAnimationName, frameRate);
        }
    }

    private static void processTxtFile(Path txtFile, Path sourceDir, Path targetDir,
                                       boolean normalizeName, String animationName,
                                       int frameRate) throws IOException {
        // 读取txt文件内容
        String content = new String(Files.readAllBytes(txtFile), StandardCharsets.UTF_8);

        try {
            // 解析原始JSON
            JSONObject originalJson = JSON.parseObject(content);

            // 转换JSON结构
            JSONObject convertedJson = convertJsonStructure(originalJson, animationName, frameRate);

            // 使用FastJSON序列化（带缩进）
            String jsonContent = JSON.toJSONString(convertedJson,
                    SerializerFeature.PrettyFormat,
                    SerializerFeature.WriteMapNullValue,
                    SerializerFeature.WriteDateUseDateFormat);

            // 获取相对路径和文件名
            Path relativePath = sourceDir.relativize(txtFile.getParent());
            String fileName = txtFile.getFileName().toString();

            // 移除.txt扩展名
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));

            // 重命名文件
            if (normalizeName) {
                baseName = animationName;
            }

            // 构建目标路径
            Path jsonDir = targetDir.resolve(relativePath);
            Files.createDirectories(jsonDir);  // 确保子目录存在
            Path jsonFile = jsonDir.resolve(baseName + ".json");

            // 写入JSON文件
            Files.write(jsonFile, jsonContent.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            System.out.println("已处理: " + txtFile + " -> " + jsonFile +
                    " (动画名称: " + animationName + ")");

        } catch (Exception e) {
            throw new IOException("处理文件失败: " + txtFile, e);
        }
    }

    /**
     * 转换JSON结构
     *
     * @param originalJson   原始JSON对象
     * @param animationName  动画名称
     * @param frameRate      帧率
     * @return 转换后的JSON对象
     */
    private static JSONObject convertJsonStructure(JSONObject originalJson,
                                                   String animationName,
                                                   int frameRate) {
        JSONArray originalFrames = originalJson.getJSONArray("frames");

        // 1. 创建目标结构
        JSONObject mc = new JSONObject();
        JSONObject res = new JSONObject();
        JSONObject result = new JSONObject();
        result.put("mc", mc);
        result.put("res", res);

        // 2. 创建动画对象
        JSONObject animation = new JSONObject();
        mc.put(animationName, animation);

        // 设置帧率和空事件数组
        animation.put("frameRate", frameRate);
//        animation.put("events", new JSONArray());

        // 3. 创建帧数组
        JSONArray framesArray = new JSONArray();
        animation.put("frames", framesArray);

        // 4. 处理每一帧
        for (int i = 0; i < originalFrames.size(); i++) {
            JSONObject originalFrame = originalFrames.getJSONObject(i);

            // 生成唯一资源ID (8位十六进制)
            String resourceId = generateResourceId(i);

            // 创建新帧对象
            JSONObject newFrame = new JSONObject();
            newFrame.put("res", resourceId);
            newFrame.put("x", -originalFrame.getIntValue("offX")); // 取负值
            newFrame.put("y", -originalFrame.getIntValue("offY")); // 取负值

            framesArray.add(newFrame);

            // 创建资源对象
            JSONObject resource = new JSONObject();
            resource.put("x", originalFrame.getIntValue("x"));
            resource.put("y", originalFrame.getIntValue("y"));
            resource.put("w", originalFrame.getIntValue("w"));
            resource.put("h", originalFrame.getIntValue("h"));

            res.put(resourceId, resource);
        }

        return result;
    }

    /**
     * 生成资源ID (8位十六进制)
     *
     * @param index 帧索引
     * @return 资源ID字符串
     */
    private static String generateResourceId(int index) {
        // 使用UUID生成唯一ID，取前8位
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid.substring(0, 8).toUpperCase();
    }

    // 示例用法
    public static void main(String[] args) {
        try {
            convertTxtToJson(
                    "D:\\test\\tianhuo",  // 替换为实际源目录
                    "D:\\test\\file_temp",  // 替换为实际目标目录
                    true,                   // 重命名文件
                    "aqq2_eff",             // 基础动画名称（将递增）
                    16                      // 帧率
            );
            System.out.println("所有文件转换完成！");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
