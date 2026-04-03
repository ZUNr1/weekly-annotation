# 手写一个迷你版 @Column：注解到底是怎么工作的？

每周一个 Java 注解底层实现 · 第 1 期

（本人也是新手，这是本人第二篇博客，打算把以前的一些笔记整合起来逐步发出来，这里先尝试开一个注解系列，聊一聊常见框架的注解）

## 一、从一个问题开始

很多 Java 开发者每天都在用 Spring、JPA、Lombok 的注解，比如：

```
@Column(name = "user_name")
private String username;
```



@Column注解可以帮助开发者将数据库的列名称与java代码的实体类的字段进行匹配

最常用的就是这个name参数，指示了该字段在数据库对应的名字

通过这个注解，开发者可以省略掉大量重复且易出错的字段映射，减轻代码复杂度

你有没有好奇过：**这个 `@Column` 到底是怎么把 `username` 和数据库里的 `user_name` 对应起来的？**

今天我们就从零实现一个极简版的 `@Column`，代码总共不超过 60 行，看完你就彻底明白注解的运行原理了。

## 二、注解的本质是什么？

注解本质上只是一个**标记**，它自己不干活。

真正干活的是**读取注解的那段代码**——通常是通过**反射**。



简单来说就是：

你写 @Column → 编译器把它存在类的元数据里 → 运行时代码通过反射读取这个标记 → 执行相应逻辑



所以今天我们要做三件事：

1. 定义 `@Column` 注解
2. 在实体类的字段上使用它
3. 写一个 `BaseDAO`，通过反射读取注解，完成字段 ↔ 数据库列的映射



## 三、第一步：定义 @Column 注解

```
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)// 运行时保留，这样才能通过反射读取
@Target(ElementType.FIELD) // 只能用在字段上
public @interface Column {
    String name(); // 数据库列名
}
```

**两个关键元注解：**

- `@Retention(RUNTIME)`：保留到运行时。如果写成 `SOURCE` 或 `CLASS`，反射就读不到了。
- `@Target(FIELD)`：限制只能放在字段上，避免被误用在类或方法上。



## 四、第二步：在实体类中使用

```
public class User {
    @Column(name = "user_id")
    private Long id;

    @Column(name = "user_name")
    private String name;

    // getter / setter 省略
}
```

这样我们就声明了：`id` 对应数据库的 `user_id` 列，`name` 对应 `user_name` 列。

## 五、第三步：核心逻辑——读取注解并填充实体

这是整个原理最核心的部分。我们写一个泛型 `BaseDAO`，里面实现 `fillEntity` 方法：

```
public class BaseDAO<T> {

    private Class<T> entityClass;

    public BaseDAO(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 从 ResultSet 读取数据，通过注解映射填充到实体对象
     */
    public void fillEntity(ResultSet rs, T entity) throws Exception {
        // 1. 获取所有带 @Column 注解的字段
        for (Field field : getMappedFields()) {
            // 2. 从注解中拿到数据库列名
            String columnName = field.getAnnotation(Column.class).name();
            // 3. 从 ResultSet 中按列名取值
            Object value = rs.getObject(columnName);
            if (value != null) {
                // 4. 暴力反射赋值（即使是 private 也能改）
                field.setAccessible(true);
                field.set(entity, value);
            }
        }
    }

    private List<Field> getMappedFields() {
        List<Field> result = new ArrayList<>();
        for (Field field : entityClass.getDeclaredFields()) {
        //遍历所有字段，找到含有注解的字段
            if (field.isAnnotationPresent(Column.class)) {
                result.add(field);
            }
        }
        return result;
    }
}
```

**核心逻辑只有 4 步：**

1. 反射拿到实体类的所有字段
2. 检查字段上有没有 `@Column` 注解
3. 从注解中取出 `name` 属性作为数据库列名
4. 从 `ResultSet` 中取值，通过反射赋给字段

这就是所有 ORM 框架（Hibernate、MyBatis、JPA）做映射的最基本原理。



## 六、第四步：跑起来看看

写一个 `main` 方法验证一下（为了简单，这里用 `Map` 模拟 `ResultSet`）：

```
public class Main {
    public static void main(String[] args) throws Exception {
        // 模拟数据库返回的一行数据
        Map<String, Object> fakeRow = new HashMap<>();
        fakeRow.put("user_id", 1001L);
        fakeRow.put("user_name", "张三");

        // 用模拟的 ResultSet（实际开发中是真实的 ResultSet）
        ResultSet rs = new MapBasedResultSet(fakeRow);

        User user = new User();
        BaseDAO<User> dao = new BaseDAO<>(User.class);
        dao.fillEntity(rs, user);

        System.out.println(user.getId());   // 输出 1001
        System.out.println(user.getName()); // 输出 张三
    }
}
```

运行结果：

```
1001
张三
```

**成功了。** 我们没有依赖任何框架，只用 JDK 原生的反射和注解，就实现了 `@Column` 的核心功能。



这样子，只要DAO层的类继承了BaseDAO，就可以使用这个fillEntity直接完成映射



## 七、延伸一下：如果注解没写 name 怎么办？

上面的代码要求 `@Column(name = "xxx")` 必须写 `name`，否则会报错。

但实际生产环境中，很多人希望不写 `name` 时自动用字段名的下划线形式（例如 `userName` → `user_name`）。

我们可以给一个**兜底机制**，修改 `getColumnName` 方法：

```
private String getColumnName(Field field) {
    Column column = field.getAnnotation(Column.class);
    if (column != null && !column.name().isEmpty()) {
        return column.name();
    }
    // 兜底：驼峰转下划线
    String name = field.getName();
    StringBuilder sb = new StringBuilder();
    for (char c : name.toCharArray()) {
        if (Character.isUpperCase(c)) {
            sb.append('_').append(Character.toLowerCase(c));
        } else {
            sb.append(c);
        }
    }
    return sb.toString();
}
```

这样即使没有显式写name，也会自动映射

当然，还是建议显式写清楚数据库的列名，这样看起来清晰易懂不易出错

## 八、这个实现的DAO还缺什么？

今天的实现虽然能跑，但离一个可用的 DAO 还差很多：

- 没有处理主键（`@Id`）
- 没有自动生成 SQL（INSERT / UPDATE / DELETE）
- 没有事务支持

当然这些就不是本篇文章想聊的了（要实现其实也简单）



下一期我们来实现 **`@Autowired` 的底层原理**——不依赖 Spring，手写一个迷你 IoC 容器。（画个饼）