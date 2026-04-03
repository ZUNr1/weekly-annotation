package org.zunr1;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class BaseDAO<T> {

    protected Class<T> entityClass;

    public BaseDAO(Class<T> entityClass) {
        this.entityClass = entityClass;
    }
    /**
     * 填充实体对象属性
     * 只填充带有 @Column 注解的字段
     */
    protected void fillEntity(ResultSet resultSet,T entity) throws Exception {
        List<Field> mappedFields = getMappedFields();
        for (Field field : mappedFields){
            String columnName = getColumnName(field);
            Object value = resultSet.getObject(columnName);
            if (value != null){
                field.setAccessible(true);
                field.set(entity,value);
            }
        }
    }

    /**
     * 获取所有需要映射的字段
     */
    protected List<Field> getMappedFields(){
        List<Field> mappedFields = new ArrayList<>();
        Field[] fields = entityClass.getDeclaredFields();
        for (Field field : fields){
            if (isMappedField(field)){
                mappedFields.add(field);
            }
        }
        return mappedFields;
    }

    /**
     * 判断字段是否需要映射到数据库
     * 规则：必须有 @Column 注解
     */
    protected boolean isMappedField(Field field) {
        // 必须有 @Column 注解才映射
        return field.isAnnotationPresent(Column.class);
    }

    /**
     * 获取字段对应的数据库列名
     * 优先使用 @Column 注解的 name 属性
     */
    protected String getColumnName(Field field){
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isEmpty()){
            return column.name();
        }
        //如果没有指定name，使用字段名转下划线（兜底机制）
        String name = field.getName();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('_').append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
