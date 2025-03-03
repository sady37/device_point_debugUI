package com.qinglan.example.device_point.ui;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qinglan.example.device_point.server.handle.SetPropHandler;
import com.qinglan.example.device_point.server.session.DeviceRegSession;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * 处理加载、解析和发送设备配置
 */
public class ConfigurationManager {
    
    private static final Logger logger = Logger.getLogger(ConfigurationManager.class.getName());
    
    /**
     * 属性设置优先级顺序
     */
    private static final String[] PROPERTY_ORDER = {
        "radar_func_ctrl",      // 工作模式优先设置
        "fall_param",           // 跌倒参数
        "heart_breath_param",   // 呼吸心率参数
        "radar_install_style",  // 安装方式
        "radar_install_height", // 安装高度
        "rectangle",            // 检测边界
        "declare_area"          // 区域设置
    };
    
    /**
     * 需要重启雷达的属性
     */
    private static final Set<String> RESTART_REQUIRED_PROPS = new HashSet<>(Arrays.asList(
        "radar_install_height", 
        "rectangle"
    ));
    
    // 区域定义属性前缀和模式
    private static final String AREA_PREFIX = "declare_area_";
    private static final Pattern AREA_PATTERN = Pattern.compile("declare_area_(\\d+)");
    
    // declare_area格式正则表达式
    private static final Pattern AREA_FORMAT_PATTERN = Pattern.compile("^(\\d+),\\d+,[\\d.,-]+$");
    private static final Pattern AREA_BRACE_PATTERN = Pattern.compile("^\\{(\\d+),(\\d+),([\\d.,-]+)\\}$");
    
    /**
     * 从区域定义字符串中提取区域ID
     * 
     * @param areaValue 区域定义字符串
     * @return 区域ID
     * @throws NumberFormatException 如果无法提取或转换区域ID
     */
    private int extractAreaId(String areaValue) throws NumberFormatException {
        if (areaValue == null || areaValue.trim().isEmpty()) {
            throw new NumberFormatException("区域值为空");
        }
        
        Matcher matcher;
        if (areaValue.startsWith("{")) {
            matcher = AREA_BRACE_PATTERN.matcher(areaValue);
        } else {
            matcher = AREA_FORMAT_PATTERN.matcher(areaValue);
        }
        
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        
        throw new NumberFormatException("无法从区域值中提取ID: " + areaValue);
    }
    
    // 设备会话访问
    private final DeviceRegSession deviceSession;
    
    // 当前加载的配置
    private JSONObject currentConfiguration;
    
    /**
     * 构造函数
     */
    public ConfigurationManager() {
        this.deviceSession = new DeviceRegSession();
        this.currentConfiguration = null;
    }
    
    /**
     * 从文件加载配置
     * 
     * @param file 配置文件
     * @return 如果加载成功返回true，否则返回false
     */
    public boolean loadConfiguration(File file) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            
            // 移除JSON中的尾随逗号
            content = removeTrailingCommas(content);
            
            try {
                JSONObject config = JSON.parseObject(content);
                
                // 基本验证
                if (!validateConfiguration(config)) {
                    logger.warning("配置格式无效");
                    return false;
                }
                
                this.currentConfiguration = config;
                logger.info("配置加载成功: " + file.getName());
                return true;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "JSON解析错误: " + e.getMessage(), e);
                // 尝试输出有问题的JSON内容的一部分（防止太长）
                if (content.length() > 500) {
                    logger.info("JSON内容摘要: " + content.substring(0, 500) + "...");
                } else {
                    logger.info("JSON内容: " + content);
                }
                return false;
            }
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "读取配置文件错误: " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "解析配置错误: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 移除JSON字符串中的尾随逗号
     * 
     * @param json 原始JSON字符串
     * @return 处理后的JSON字符串
     */
    private String removeTrailingCommas(String json) {
        // 移除对象内的尾随逗号
        json = json.replaceAll(",\\s*}", "}");
        
        // 移除数组内的尾随逗号
        json = json.replaceAll(",\\s*]", "]");
        
        return json;
    }
    
    /**
     * 验证配置格式
     * 
     * @param config 要验证的配置
     * @return 如果有效则为true，否则为false
     */
    private boolean validateConfiguration(JSONObject config) {
        // 基本验证 - 检查必填字段
        if (config == null || config.isEmpty()) {
            logger.warning("配置为空或格式不正确");
            return false;
        }
        
        // 检查properties字段
        if (!config.containsKey("properties")) {
            logger.warning("缺少properties字段");
            return false;
        }
        
        JSONObject properties = config.getJSONObject("properties");
        if (properties == null || properties.isEmpty()) {
            logger.warning("properties字段为空");
            return false;
        }
        
        // 验证declare_area属性格式
        Set<Integer> areaIds = new HashSet<>();
        for (String key : properties.keySet()) {
            if (key.equals("declare_area") || key.startsWith(AREA_PREFIX)) {
                String value = properties.getString(key);
                
                // 检查JSON格式是否正确（没有尾随逗号）
                if (value == null) {
                    logger.warning("属性值为null: " + key);
                    return false;
                }
                
                if (!validateAreaFormat(value)) {
                    logger.warning("区域定义格式错误: " + key + "=" + value);
                    return false;
                }
                
                // 提取区域ID并检查是否重复
                try {
                    // 根据格式选择合适的正则
                    Matcher matcher;
                    if (value.startsWith("{")) {
                        matcher = AREA_BRACE_PATTERN.matcher(value);
                    } else {
                        matcher = AREA_FORMAT_PATTERN.matcher(value);
                    }
                    
                    if (matcher.find()) {
                        int areaId = Integer.parseInt(matcher.group(1));
                        
                        // 检查区域ID与键名是否一致（如果是命名的区域）
                        if (key.startsWith(AREA_PREFIX)) {
                            try {
                                int keyId = Integer.parseInt(key.substring(AREA_PREFIX.length()));
                                if (areaId != keyId) {
                                    logger.warning("区域ID与键名不匹配: 键=" + key + ", ID=" + areaId);
                                    // 仅警告，不返回false
                                }
                            } catch (NumberFormatException e) {
                                logger.warning("键名后缀不是有效数字: " + key);
                                // 仅警告，不返回false
                            }
                        }
                        
                        if (areaIds.contains(areaId)) {
                            logger.warning("区域ID重复: " + areaId);
                            return false;
                        }
                        areaIds.add(areaId);
                    } else {
                        logger.warning("无法提取区域ID: " + value);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    logger.warning("区域ID格式错误: " + value);
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 验证区域定义格式
     * 
     * @param areaValue 区域定义字符串
     * @return 如果格式正确则为true，否则为false
     */
    private boolean validateAreaFormat(String areaValue) {
        if (areaValue == null || areaValue.trim().isEmpty()) {
            logger.warning("区域值为空");
            return false;
        }
        
        // 检查格式：area-id,area-type,x1,y1,x2,y2,...
        boolean isStandardFormat = AREA_FORMAT_PATTERN.matcher(areaValue).matches();
        
        // 检查带花括号的格式：{area-id,area-type,x1,y1,x2,y2,...}
        boolean isBraceFormat = AREA_BRACE_PATTERN.matcher(areaValue).matches();
        
        if (!isStandardFormat && !isBraceFormat) {
            logger.warning("区域格式不正确: " + areaValue);
        }
        
        return isStandardFormat || isBraceFormat;
    }
    
    /**
     * 标准化区域定义格式（移除花括号等）
     * 
     * @param areaValue 原始区域定义字符串
     * @return 标准化后的区域定义字符串
     */
    private String normalizeAreaValue(String areaValue) {
        if (areaValue == null || areaValue.trim().isEmpty()) {
            return areaValue;
        }
        
        // 移除花括号
        if (areaValue.startsWith("{") && areaValue.endsWith("}")) {
            return areaValue.substring(1, areaValue.length() - 1);
        }
        
        return areaValue;
    }
    
    /**
     * 从配置中获取属性值
     * 
     * @param key 属性键
     * @return 属性值，如果未找到则为null
     */
    public String getPropertyValue(String key) {
        if (currentConfiguration == null) {
            return null;
        }
        
        JSONObject properties = currentConfiguration.getJSONObject("properties");
        if (properties == null) {
            return null;
        }
        
        return properties.getString(key);
    }
    
    /**
     * 获取配置中的所有属性
     * 
     * @return 属性键到值的映射
     */
    public Map<String, String> getAllProperties() {
        Map<String, String> result = new HashMap<>();
        
        if (currentConfiguration == null) {
            return result;
        }
        
        JSONObject properties = currentConfiguration.getJSONObject("properties");
        if (properties == null) {
            return result;
        }
        
        for (String key : properties.keySet()) {
            result.put(key, properties.getString(key));
        }
        
        return result;
    }
    
    /**
     * 向设备发送配置
     * 
     * @param deviceId 要发送到的设备ID
     * @return 如果成功则为true，否则为false
     */
    public boolean sendConfigurationToDevice(String deviceId) {
        if (currentConfiguration == null) {
            logger.warning("未加载配置");
            return false;
        }
        
        try {
            // 获取所有属性
            Map<String, String> allProperties = getAllProperties();
            
            // 收集区域定义（以declare_area或declare_area_X格式）
            List<String> areaDefinitions = new ArrayList<>();
            List<String> keysToRemove = new ArrayList<>();
            
            // 处理主区域属性
            if (allProperties.containsKey("declare_area")) {
                String areaValue = allProperties.get("declare_area");
                if (areaValue != null && !areaValue.trim().isEmpty()) {
                    areaDefinitions.add(areaValue);
                    logger.info("添加主区域定义: " + areaValue);
                }
                keysToRemove.add("declare_area");
            }
            
            // 处理带索引的区域属性
            for (Map.Entry<String, String> entry : allProperties.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(AREA_PREFIX)) {
                    String areaValue = entry.getValue();
                    if (areaValue != null && !areaValue.trim().isEmpty()) {
                        areaDefinitions.add(areaValue);
                        logger.info("添加索引区域定义: " + key + "=" + areaValue);
                    }
                    keysToRemove.add(key);
                }
            }
            
            // 从原始属性中移除区域定义
            for (String key : keysToRemove) {
                allProperties.remove(key);
            }
            
            boolean needsRestart = false;
            boolean allSuccess = true;
            
            // 按优先级顺序设置属性
            for (String key : PROPERTY_ORDER) {
                // 跳过区域设置，稍后单独处理
                if (key.equals("declare_area")) {
                    continue;
                }
                
                if (allProperties.containsKey(key)) {
                    String value = allProperties.get(key);
                    logger.info("按顺序设置属性: " + key + "=" + value);
                    
                    boolean success = sendPropertyToDevice(deviceId, key, value);
                    if (!success) {
                        logger.warning("设置属性失败: " + key + "=" + value);
                        allSuccess = false;
                    }
                    
                    // 检查是否需要最终重启
                    if (RESTART_REQUIRED_PROPS.contains(key)) {
                        needsRestart = true;
                    }
                    
                    // 每设置一项，等待300ms
                    Thread.sleep(300);
                }
            }
            
            // 设置未在优先级列表中的属性（区域定义除外）
            for (Map.Entry<String, String> entry : allProperties.entrySet()) {
                String key = entry.getKey();
                // 跳过已经设置过的属性
                if (Arrays.asList(PROPERTY_ORDER).contains(key)) {
                    continue;
                }
                
                String value = entry.getValue();
                logger.info("设置额外属性: " + key + "=" + value);
                
                boolean success = sendPropertyToDevice(deviceId, key, value);
                if (!success) {
                    logger.warning("设置额外属性失败: " + key + "=" + value);
                    allSuccess = false;
                }
                
                // 每设置一项，等待300ms
                Thread.sleep(300);
            }
            
            // 按区域ID排序区域定义
            areaDefinitions.sort((a, b) -> {
                try {
                    // 根据格式提取区域ID进行比较
                    int idA = extractAreaId(a);
                    int idB = extractAreaId(b);
                    return Integer.compare(idA, idB);
                } catch (NumberFormatException e) {
                    logger.warning("排序区域定义时出错: " + e.getMessage());
                }
                return a.compareTo(b);
            });
            
            // 逐个设置区域定义
            for (String areaValue : areaDefinitions) {
                // 标准化区域值（移除花括号等）
                String normalizedValue = normalizeAreaValue(areaValue);
                logger.info("设置区域: declare_area=" + normalizedValue);
                
                // 使用基础键名"declare_area"发送
                boolean success = sendPropertyToDevice(deviceId, "declare_area", normalizedValue);
                if (!success) {
                    logger.warning("设置区域失败: declare_area=" + normalizedValue);
                    allSuccess = false;
                }
                
                // 区域设置之间等待更长时间，确保每个区域设置都能完成
                Thread.sleep(1000);
            }
            
            // 如果需要重启且有高度或边界设置，等待5秒然后重启设备
            if (needsRestart) {
                logger.info("配置需要设备重启，等待5秒后重启设备: " + deviceId);
                
                Thread.sleep(5000);
                
                // 发送重启命令
                boolean restartSuccess = SetPropHandler.restartDevice(deviceId);
                if (!restartSuccess) {
                    logger.warning("重启设备失败: " + deviceId);
                    allSuccess = false;
                }
            }
            
            logger.info("配置" + (allSuccess ? "完全" : "部分") + "发送到设备: " + deviceId);
            return allSuccess;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "发送配置错误", e);
            return false;
        }
    }
    
    /**
     * 向设备发送单个属性
     * 
     * @param deviceId 设备ID
     * @param key 属性键
     * @param value 属性值
     * @return 如果成功则为true，否则为false
     */
    private boolean sendPropertyToDevice(String deviceId, String key, String value) {
        return SetPropHandler.setProperty(deviceId, key, value);
    }
    
    /**
     * 检查是否加载了配置
     * 
     * @return 如果加载了配置则为true，否则为false
     */
    public boolean isConfigurationLoaded() {
        return currentConfiguration != null;
    }
}