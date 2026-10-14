# SysY ARM Compiler

> 从一行 SysY 源码，到一段可以交给 ARM 工具链的汇编。

![Java](https://img.shields.io/badge/Java-11%2B-ED8B00?logo=openjdk&logoColor=white)
![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)
![Target](https://img.shields.io/badge/target-ARMv7VE-0091BD?logo=arm&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-green)

这是一个使用 Java 编写的 SysY 编译器：读取 SysY 源程序，经过前端分析、自定义中间表示（MIR）、中端优化和 ARM 后端，最终生成 GNU 汇编语法的 ARMv7VE 汇编代码。

本项目是 **2023 年全国大学生计算机系统能力大赛——编译系统设计赛决赛参赛作品** 的开源整理版。仓库用于保存参赛实现、分享编译器设计思路并为后续维护提供一个干净的工程基础；它不是赛事官方项目，也不代表赛事官方立场。

## 目录

- [功能概览](#功能概览)
- [整体架构](#整体架构)
- [前端设计](#前端设计)
- [MIR 设计](#mir-设计)
- [分析与优化](#分析与优化)
- [ARM 后端](#arm-后端)
- [项目结构](#项目结构)
- [构建与使用](#构建与使用)
- [开发说明](#开发说明)
- [项目状态](#项目状态)

## 功能概览

编译器当前覆盖的主要 SysY 语言元素如下：

| 类别 | 支持内容 |
| --- | --- |
| 数据类型 | int、float、void |
| 声明 | 常量、全局/局部变量、多维数组及初始化 |
| 表达式 | 算术、比较、逻辑、赋值和一元表达式 |
| 控制流 | if/else、while、break、continue、return |
| 函数 | 函数定义、参数传递、递归与函数调用 |
| SysY 运行时 | getint、getch、getfloat、数组输入输出、计时函数等 |
| 目标代码 | ARMv7VE，VFPv3-D16 浮点指令，GNU 汇编语法 |

工程与实现方面包括：

- 使用 ANTLR 4 描述 SysY 词法、语法并生成解析器；
- 构建独立 AST 和带作用域链的符号表；
- 使用 LLVM 风格、由项目自行实现的 MIR；
- 维护 CFG、支配关系、循环信息、函数副作用和 Value–Use 关系；
- 实现 Mem2Reg、函数内联、GVN、GCM、DCE、循环优化等 Pass；
- 分别完成整数与浮点虚拟寄存器的图着色分配；
- 处理寄存器溢出重写、Phi 消除和函数栈帧维护。

> MIR 借鉴了 LLVM IR 的 SSA、基本块和 Value–Use 思想，但本项目不依赖 LLVM，也不保证 MIR 文本能被 LLVM 工具直接读取。

## 整体架构

编译器按前端、中端和后端组织。各阶段通过明确的数据结构衔接：前端产生 AST 与 MIR，中端只操作 MIR，后端将 MIR 降低到 ARM 指令模型。

~~~mermaid
flowchart LR
    A[SysY 源文件] --> B[ANTLR Lexer / Parser]
    B --> C[AST]
    C --> D[符号表与语义转换]
    D --> E[MIR / SSA]
    E --> F[CFG、支配与循环分析]
    F --> G[中端优化流水线]
    G --> H[ARM 指令选择]
    H --> I[活跃变量分析]
    I --> J[整数/浮点寄存器分配]
    J --> K[栈帧维护与汇编输出]
~~~

| 阶段 | 输入 | 主要工作 | 输出 |
| --- | --- | --- | --- |
| 前端 | SysY 字符流 | 词法语法分析、AST、符号与类型处理 | 初始 MIR |
| 中端 | MIR | SSA、数据流/控制流分析和优化 Pass | 优化后的 MIR |
| 后端 | MIR | 指令选择、Phi 消除、寄存器分配、栈帧维护 | ARM 汇编 |

入口类是 [Compiler](src/main/java/Compiler.java)。它按固定顺序调度各阶段，避免单个优化 Pass 在前置分析尚未建立时运行。

## 前端设计

### 词法与语法分析

SysY 文法定义在 [Sysy.g4](src/main/antlr4/frontend/lexerparser/Sysy.g4)。构建时，antlr4-maven-plugin 会生成 Lexer、Parser、Listener 和 Visitor；生成文件位于 target/generated-sources/antlr4，不进入版本库。

[Builder](src/main/java/frontend/ast/Builder.java) 完成解析器装配：

1. 从源文件建立 CharStream；
2. 使用 SysyLexer 产生 Token 流；
3. 使用 SysyParser 构建解析树；
4. 将解析结果转换为项目自己的 AST。

### AST

AST 节点集中定义在 [Node](src/main/java/frontend/ast/Node.java) 中，覆盖声明、函数、语句、数组初始化和各类表达式。ANTLR 生成的解析树只负责识别文法，后续阶段依赖的是这套与编译器实现解耦的 AST。

这种分层让语法工具与 IR 生成相互独立：修改 MIR 时不需要改动 ANTLR 生成类，修改文法时也不必让中端理解解析树细节。

### 符号与作用域

[SymTable](src/main/java/frontend/sym/SymTable.java) 使用父指针构成作用域链。每个符号记录名称、常量属性、类型、初始化值以及对应的 MIR 地址。

前端访问器进入函数或嵌套代码块时创建子符号表，离开作用域后回到父表；名字查询可以按需向外层作用域递归。数组维度与常量初值在这一阶段计算并转换为 MIR 类型。

### 从 AST 到 MIR

[MyVisitor](src/main/java/frontend/visitor/MyVisitor.java) 遍历 AST，同时完成：

- 基本块和函数的创建；
- 局部变量 alloca/load/store 形式的初始建模；
- 整数、浮点和布尔值之间的 MIR 类型转换；
- 数组类型、初始化与地址计算；
- if/else、循环和短路逻辑的控制流构建；
- 函数参数、返回值以及 SysY 运行时函数调用的生成。

break/continue 目标通过循环入口与出口栈维护，使嵌套循环能够跳转到正确的基本块。

## MIR 设计

MIR 是编译器中端的核心。它采用 Module → Function → BasicBlock → Instruction 的层级，并以 [Value](src/main/java/mir/Value.java) 作为常量、全局对象、参数和指令结果的统一抽象。

~~~text
MyModule
├── GlobalValue
└── Func
    └── BasicBlock
        └── Instr
            ├── Alloca / Load / Store / Gep
            ├── Alu / Icmp / Fcmp
            ├── Phi / Move
            ├── Branch / Jump / Ret / Call
            └── Zext / Trunc / Sitofp / Fptosi ...
~~~

### Value–Use 关系

每条指令既是操作，也是可以被其他指令引用的 Value。Value 保存使用它的指令列表，Instr 保存自己的操作数，从而形成双向的 Def–Use/Use–Def 关系。

这一设计为优化提供了统一接口。例如 replaceAllUsesWith 可以把一个值的所有使用替换为新值；DCE 可以从有副作用的根指令反向标记依赖；GVN 可以用已有值替换重复计算。

多数 Pass 在移动或删除指令时都会同步维护这些关系。项目还使用自定义双向链表 [MyList](src/main/java/tools/MyList.java) 存放基本块和指令，方便在迭代过程中执行插入、删除与重排。

### SSA 与 Phi

前端先用内存形式表达局部变量，随后 Mem2Reg 基于支配边界插入 Phi，并沿支配树进行变量重命名，将可提升的标量转换为 SSA 形式。

后端生成前，[RemovePhi](src/main/java/midend/RemovePhi.java) 将 Phi 转为并行复制：

- 普通复制在前驱块末尾插入；
- 关键边会拆分出新的中间基本块；
- 并行复制存在环时使用临时虚拟值断环。

## 分析与优化

### 基础分析

中端 Pass 共享以下分析结果：

| 分析 | 实现 | 用途 |
| --- | --- | --- |
| CFG 构建 | CFGBuilder | 维护基本块前驱和后继 |
| 支配分析 | DomInfo | 支配树、支配深度和支配边界 |
| 循环分析 | LoopAnalysis | 入口、回边、出口、嵌套与循环深度 |
| 归纳变量识别 | LoopInductionVariableGet | 识别 Phi、步长和循环比较关系 |
| 副作用分析 | SideEffectAnalysis | 判断调用和内存访问能否安全删除或移动 |
| 活跃变量分析 | LiveAnalysisTool | 为后端干扰图与寄存器分配提供信息 |

### 优化流水线

实际启用的优化由 [Compiler](src/main/java/Compiler.java) 按下列大致顺序调度：

~~~text
CFG / Alloca 预处理
  → 全局变量局部化、无用返回值消除
  → Mem2Reg
  → 函数内联
  → DCE、无用 Store 消除、指令合并
  → LCSSA、无用循环删除、LICM、循环折叠
  → 分支优化
  → GVN → DCE → GCM
  → 算术折叠、乘除模优化、基本块重排
  → 重复 Load 消除
  → Phi 消除
~~~

| Pass | 主要作用 |
| --- | --- |
| GlobalValueLocalize | 将只在局部使用或可常量化的全局值下沉/替换 |
| UselessRetValEmit | 当所有调用点都忽略返回值时，删除无意义的返回值传播 |
| Mem2Reg | 插入 Phi、沿支配树重命名并把局部标量提升到 SSA |
| FuncInline | 克隆被调函数 CFG，处理参数、返回值和调用点后的 Phi |
| SimpleDCE / DeadCodeElimination | 根据 users 数量或从副作用根反向标记删除死代码 |
| UselessStoreEmit | 删除同一基本块中、读取前被后续写覆盖的 Store |
| LocalSameLoadEmit | 复用基本块内同一地址的可证明等价 Load |
| InstrCombine / AddFold | 合并加减链并折叠可化简的算术表达式 |
| LCSSA | 为循环内定义、循环外使用的值在出口处建立 Phi |
| LoopUselessDel | 删除结果无用且没有可观察副作用的循环 |
| LoopInvariantCodeMotion | 将安全的循环不变量移动到循环外 |
| LoopFold | 利用归纳变量信息折叠满足条件的循环结构 |
| BranchOpt | 合并基本块、清理不可达块、简化分支和单输入 Phi |
| GVN | 常量折叠，并通过表达式哈希消除全局冗余值 |
| GCM | 根据支配关系和循环深度做全局代码移动 |
| DivMulMod | 将部分乘法改写为移位，并展开取模等运算 |
| BlockOrderAdjust | 按控制流 DFS 调整基本块输出顺序 |
| RemovePhi | 将 SSA Phi 降低为并行复制与 Move |

源码中还保留了 LoopUnrolling 等实验实现，但当前默认流水线没有启用，以保持决赛版本的实际调度行为清晰可见。

## ARM 后端

### 指令与操作数模型

[ArmTools](src/main/java/arm/armDS/ArmTools.java) 负责把 MIR 降低为后端对象。后端同样采用模块、函数、基本块和指令的层级，并区分立即数、物理寄存器、整数虚拟寄存器和浮点虚拟寄存器。

主要支持的指令类别包括：

- 整数/浮点算术和 FMA；
- 比较、条件执行、分支与函数调用；
- Load/Store、Move 和地址计算；
- 整数与浮点之间的转换；
- ARM 条件码和移位操作数。

后端保留 MIR 基本块与 ARM 基本块之间的映射，使活跃变量分析可以继续复用中端 CFG。

### 图着色寄存器分配

整数和浮点寄存器由两套分配器分别处理。整体过程为：

1. 计算每个基本块的 liveUse、liveDef、liveIn 和 liveOut；
2. 根据同时活跃关系建立干扰图；
3. 反复执行 simplify、coalesce、freeze 和 spill 选择；
4. 从选择栈逆序分配颜色；
5. 若发生溢出，则插入访存并重写程序，再重新分析与着色。

Move 合并用于减少不必要的寄存器复制；循环深度等信息参与溢出选择。分配结束后，[MaintainStack](src/main/java/arm/regallocator/MaintainStack.java) 根据实际用到的被调用者保存型整数/浮点寄存器以及 lr 维护栈帧。

### 汇编输出

输出文件包含：

~~~asm
.arch armv7ve
.fpu vfpv3-d16
.text
~~~

函数、基本块、全局变量和数组随后被序列化为 GNU 风格 ARM 汇编。生成文件仍需要 SysY 运行时库和相应 ARM 工具链完成汇编、链接与运行。

## 项目结构

~~~text
.
├── .github/workflows/ci.yml     # GitHub Actions 构建与冒烟测试
├── examples/
│   └── hello.sy                 # 最小 SysY 示例
├── src/main/antlr4/
│   └── frontend/lexerparser/
│       └── Sysy.g4              # SysY 文法
├── src/main/java/
│   ├── Compiler.java            # 编译器命令行入口与 Pass 调度
│   ├── compiler/                # 文件输入、AST 到 MIR 的入口辅助
│   ├── frontend/
│   │   ├── ast/                 # AST 构建与节点定义
│   │   ├── sym/                 # 符号、作用域、常量初始化
│   │   └── visitor/             # AST 到 MIR
│   ├── mir/
│   │   ├── type/                # MIR 类型系统
│   │   └── *.java               # Value、Instr、BasicBlock、Func、Module
│   ├── midend/
│   │   ├── analysis/            # CFG、支配、循环、副作用分析
│   │   └── *.java               # 优化 Pass
│   ├── arm/
│   │   ├── armDS/               # ARM 模块、函数、基本块和指令基类
│   │   ├── arminstructions/     # 具体 ARM 指令
│   │   ├── armoperands/         # 立即数与寄存器操作数
│   │   └── regallocator/        # 活跃分析、图着色与栈帧维护
│   └── tools/                   # 双向链表等通用数据结构
├── compiler                     # 可执行启动脚本
└── pom.xml                      # Maven 构建配置
~~~

原始参赛压缩包中的 .class、IDE 私有配置、重复 ANTLR 生成物等不属于源码，已从开源版本中移除。所有生成结果统一进入被 Git 忽略的 target/。

## 构建与使用

### 环境要求

- JDK 11 或更高版本；
- Apache Maven 3.8 或更高版本。

### 构建

~~~bash
mvn clean package
~~~

Maven 会从 Sysy.g4 生成解析器，并打包一个包含 ANTLR 运行时的可执行 JAR：

~~~text
target/sysy-arm-compiler.jar
~~~

### 编译 SysY 程序

~~~bash
java -jar target/sysy-arm-compiler.jar \
  -S -o target/hello.s examples/hello.sy
~~~

也可以使用仓库根目录下的启动脚本：

~~~bash
./compiler -S -o target/hello.s examples/hello.sy
~~~

比赛兼容的命令行格式为：

~~~text
compiler -S -o <output.s> <input.sy> [-O1]
~~~

-O1 为比赛接口兼容参数。当前开源整理版在带或不带该参数时都会运行同一套决赛优化流水线。

### 汇编与运行

编译器只负责生成 ARM 汇编。继续执行生成程序还需要：

1. ARM Linux 环境或 ARM 交叉编译工具链；
2. 比赛提供的 SysY 运行时库 sylib；
3. 与输出匹配的 ARMv7VE/VFP 浮点 ABI 配置。

运行时库和比赛测试集不包含在本仓库中。

## 开发说明

### 代码格式

项目通过 Spotless 和 Google Java Format（AOSP 风格）管理手写 Java 源码：

~~~bash
mvn spotless:apply
mvn spotless:check
~~~

请勿手工修改或提交 target/generated-sources/antlr4 下的 ANTLR 生成代码。

### 修改编译器时需要注意

- 删除或替换 MIR 指令时，同时维护操作数的 users；
- 改变 CFG 后，重新运行 CFG、支配或循环分析；
- 函数内联应位于依赖旧循环结构的分析之前；
- GVN 后需要配合 GCM 调整激进替换后的指令位置；
- 修改 Phi、前驱或后继时，保持输入值与来源基本块一一对应；
- 后端新增指令时，实现准确的 def/use 寄存器集合，供活跃分析使用。

### 持续集成

GitHub Actions 会在 push 和 pull request 时：

1. 使用 JDK 11 执行 Maven 构建；
2. 用 examples/hello.sy 运行一次完整编译；
3. 检查输出汇编非空且包含 main 标签。

## 项目状态

这是对 2023 年决赛代码的工程化整理，保留了原有数据结构、优化流水线和后端实现。它适合用于：

- 学习一个较完整的 SysY 编译器如何从 AST 走到 ARM；
- 阅读 SSA、Value–Use、循环分析和图着色寄存器分配的实现；
- 复现参赛版本并继续增加分析、优化或测试。

同时也请注意：

- 当前错误诊断以编译成功路径为主，不是完整的用户友好诊断系统；
- 某些边界条件和实验 Pass 仍保留参赛时期的实现与注释；
- 原赛事完整功能/性能测试集未随压缩包提供；
- 本项目是教学与竞赛性质的编译器，不应直接视为生产级工具链。

欢迎通过 Issue 记录可复现问题，或通过 Pull Request 补充测试、文档和优化实现。

## 参考资料

- [ANTLR 4](https://www.antlr.org/)
- [LLVM Language Reference Manual](https://llvm.org/docs/LangRef.html)（MIR/SSA 思想参考）
- [ARM Developer Documentation](https://developer.arm.com/documentation)
- [全国大学生计算机系统能力大赛](https://compiler.educg.net/)

## 许可证

本项目采用 [MIT License](LICENSE) 开源。

## 致谢

感谢全国大学生计算机系统能力大赛编译系统设计赛的组织者、命题与评测团队，感谢 SysY、ANTLR、LLVM 与 ARM 生态提供的规范、工具和设计资料，也感谢所有参与原始参赛实现与后续整理的贡献者。
