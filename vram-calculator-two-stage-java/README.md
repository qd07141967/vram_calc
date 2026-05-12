# 双阶段显存计算器 Java 实现

该目录是双阶段显存计算器的 Java 原型实现。阶段一计算模型显存需求明细，阶段二基于规格列表一次性评估 `single_instance`、`multi_instance`、`cluster` 三种部署模式。

## 关键设计

阶段一的架构公式使用 Aviator 表达式引擎计算。不同架构的 KV Cache/状态显存公式维护在 `VramInfoConfig` 中，新增架构时优先只新增一条 `ArchitectureFormulaConfig`，不需要在 `VramRequirementCalculator` 里新增 `switch` 或 `if` 分支。

请求和结果对象使用 Lombok 生成标准 Java Bean getter、构造器和 `toString`，不使用 `@Accessors(fluent = true)`。为了保留不可变拷贝、字段标准化等关键逻辑，部分输入对象仍保留手写构造器。

## 结构

```text
src/main/java/com/wmdoc/vram2/
  calculator/
    VramRequirementCalculator.java       # 阶段一：计算显存需求明细
    SpecificationVramEvaluator.java      # 阶段二：规格列表显存消耗评估
  config/
    VramInfoConfig.java                  # 阶段一配置，包含架构表达式、量化和框架预留
    SpecResourceParseConfig.java         # 阶段二规格资源解析配置
  model/
    VramRequirementRequest.java          # 阶段一输入
    VramRequirementResult.java           # 阶段一输出
    SpecificationVramEvaluationRequest.java
    SpecificationVramEvaluationResult.java
    SpecificationInput.java
    SpecResourceInput.java
    DeploymentVramResult.java
  common/
    MemoryUnit.java                      # 显存单位转换
  demo/
    VramCalculatorDemo.java              # 示例入口

src/test/java/com/wmdoc/vram2/calculator/
  VramCalculatorTest.java                # JUnit 5 测试
```

## 运行

项目依赖 Aviator、Lombok 和 JUnit 5，建议用 Maven 导入 IDE 或编译：

```powershell
mvn test
```

如需运行示例类：

```powershell
mvn -q dependency:build-classpath '-Dmdep.outputFile=cp.txt'
$cp = Get-Content cp.txt
java -cp "target/classes;$cp" com.wmdoc.vram2.demo.VramCalculatorDemo
```
