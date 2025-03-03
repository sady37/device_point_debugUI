# a simple demo for wifi radar
server:10.0.0.200:1060 radar: uid:25A859B8333B deviceID:TSBLU333B 
===20250228 10:47
Radar Debug UI 实现方案总结
总体思路
创建一个基于Java Swing/JavaFX的独立UI模块，与现有雷达服务器无缝集成，实现设备连接监控、配置下发和数据实时显示。
新增文件

RadarDebugUI.java - 主窗口界面，负责整体UI布局
EventBus.java - 简单事件分发系统，连接后端和UI
RadarUIController.java - 业务逻辑控制器
DeviceSessionListener.java - 会话事件监听接口
ConfigurationManager.java - 配置文件管理

需修改的现有文件

DeviceRegSession.java - 添加事件通知机制
QlIotServer.java - 增加UI访问接口
DevicePointApplication.java - 添加UI启动选项

核心功能

设备连接管理 - 显示和控制设备连接状态
配置下发 - 加载JSON配置并发送到设备
实时数据监控 - 显示发送/接收/心跳数据

集成原理

使用观察者模式实现松耦合事件通知
通过共享会话访问设备连接
利用现有通道发送命令
保持独立模块结构，最小化对现有代码的修改

目录：
# 创建UI包目录
New-Item -Path "src/main/java/com/qinglan/example/device_point/ui" -ItemType Directory -Force

# 创建主要Java文件
New-Item -Path "src/main/java/com/qinglan/example/device_point/ui/RadarDebugUI.java" -ItemType File -Force
New-Item -Path "src/main/java/com/qinglan/example/device_point/ui/EventBus.java" -ItemType File -Force
New-Item -Path "src/main/java/com/qinglan/example/device_point/ui/RadarUIController.java" -ItemType File -Force
New-Item -Path "src/main/java/com/qinglan/example/device_point/ui/DeviceSessionListener.java" -ItemType File -Force
New-Item -Path "src/main/java/com/qinglan/example/device_point/ui/ConfigurationManager.java" -ItemType File -Force

# 创建资源目录和配置文件（可选）
New-Item -Path "src/main/java/com/qinglan/example/device_point/ui/resources" -ItemType Directory -Force
New-Item -Path "src/main/java/com/qinglan/example/device_point/ui/resources/default-config.json" -ItemType File -Force

mvn clean install -e
java "-Djava.awt.headless=false" -jar target/device_point-0.0.1-SNAPSHOT.jar --ui

===20250301 00:35
v0.1  完成debugUI,设备注册、接收心跳


===20250302
message CommonMessage   与  CommonMessage方法是两个独立的，只是名字一样；
message CommonMessage {
uint32 seq = 1; //包序列号  这个序列号即是type值

v0.2 实现了设备属性设置，设备返回设置状态， 查询设备属性

===20250303
v0.3  解决了json包含多个设备属性，特别是区域，按优先顺序，每次只发送一个


