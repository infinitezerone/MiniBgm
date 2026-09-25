# `:feature:schedule`

## 🎯 模块职责
提供放送 Tab 的展示与交互；通过 `:core:data` 消费放送数据，不直接访问网络或数据库。

## 🏛️ 依赖关系
* **依赖的上游**：feature convention plugin 提供的 `:core:*` 依赖与 Navigation 3
* **被谁依赖**：`:app`
* **禁止依赖**：任何其他 `:feature:*` 模块

## ⚠️ 架构红线与约束
1. 不依赖任何其他 feature。
2. 导航意图经回调交由 `:app` 聚合；模块不持有全局导航容器。
3. ViewModel 必须由导航 entry 获取，保持 entry 级作用域。

## Module dependency graph

<!--region graph-->
```mermaid
---
config:
  layout: elk
  elk:
    nodePlacementStrategy: SIMPLE
---
graph TB
  subgraph :feature
    direction TB
    :feature:schedule[schedule]:::android-feature
  end
  subgraph :core
    direction TB
    :core:common[common]:::kmp-library
    :core:data[data]:::kmp-library
    :core:database[database]:::kmp-library
    :core:datastore[datastore]:::kmp-library
    :core:designsystem[designsystem]:::android-library
    :core:model[model]:::kmp-library
    :core:navigation[navigation]:::android-library
    :core:network[network]:::kmp-library
  end

  :core:data -.->|commonMainImplementation| :core:common
  :core:data -.->|commonMainImplementation| :core:database
  :core:data -.->|commonMainImplementation| :core:datastore
  :core:data -.->|commonMainImplementation| :core:model
  :core:data -.->|commonMainImplementation| :core:network
  :core:database -.->|commonMainImplementation| :core:common
  :core:database -.->|commonMainImplementation| :core:model
  :core:datastore -.->|commonMainImplementation| :core:common
  :core:datastore -.->|commonMainImplementation| :core:model
  :core:designsystem -.-> :core:common
  :core:navigation -.-> :core:common
  :core:network -.->|commonMainImplementation| :core:common
  :core:network -.->|commonMainImplementation| :core:model
  :feature:schedule -.-> :core:common
  :feature:schedule -.-> :core:data
  :feature:schedule -.-> :core:designsystem
  :feature:schedule -.-> :core:model
  :feature:schedule -.-> :core:navigation

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
```

<details><summary>📋 Graph legend</summary>

```mermaid
graph TB
  application[application]:::android-application
  feature[feature]:::android-feature
  kmp library[kmp library]:::kmp-library
  android library[android library]:::android-library

  application -.-> feature
  library --> kmp library

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;
```

</details>
<!--endregion-->
