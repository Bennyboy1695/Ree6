package de.presti.ree6.utils.data;

import de.presti.ree6.sql.base.annotations.Property;
import de.presti.ree6.sql.base.annotations.Table;
import de.presti.ree6.sql.base.data.SQLEntity;
import de.presti.ree6.sql.base.data.SQLParameter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLUtil class to help with SQL-Queries.
 */
public class SQLUtil {

    /**
     * Constructor for the SqlUtil class.
     */
    private SQLUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Get the name of the SQL DataTyp e.g. "VARCHAR".
     * From their Java equivalent: e.g. "String" -> "VARCHAR".
     *
     * @param javaObjectClass the Java ObjectClass.
     * @return {@link String} as the SQL DataType.
     */
    public static String mapJavaToSQL(Class<?> javaObjectClass) {
        if (javaObjectClass.isAssignableFrom(String.class)) {
            return "VARCHAR(255)";
        } else if (javaObjectClass.isAssignableFrom(Integer.class) ||
                javaObjectClass.isAssignableFrom(int.class)) {
            return "INT";
        } else if (javaObjectClass.isAssignableFrom(Long.class) ||
                javaObjectClass.isAssignableFrom(long.class)) {
            return "BIGINT";
        } else if (javaObjectClass.isAssignableFrom(Boolean.class) ||
                javaObjectClass.isAssignableFrom(boolean.class)) {
            return "BOOLEAN";
        } else if (javaObjectClass.isAssignableFrom(Double.class) ||
                javaObjectClass.isAssignableFrom(double.class)) {
            return "DOUBLE";
        } else if (javaObjectClass.isAssignableFrom(Float.class) ||
                javaObjectClass.isAssignableFrom(float.class)) {
            return "FLOAT";
        } else if (javaObjectClass.isAssignableFrom(Short.class) ||
                javaObjectClass.isAssignableFrom(short.class)) {
            return "SMALLINT";
        } else if (javaObjectClass.isAssignableFrom(Byte.class) ||
                javaObjectClass.isAssignableFrom(byte.class)) {
            return "TINYINT";
        } else if (javaObjectClass.isAssignableFrom(Character.class) ||
                javaObjectClass.isAssignableFrom(char.class)) {
            return "CHAR(1)";
        } else if (javaObjectClass.isAssignableFrom(java.util.Date.class)) {
            return "DATETIME";
        } else if (javaObjectClass.isAssignableFrom(java.sql.Date.class)) {
            return "DATE";
        } else if (javaObjectClass.isAssignableFrom(java.sql.Time.class)) {
            return "TIME";
        } else if (javaObjectClass.isAssignableFrom(java.sql.Timestamp.class)) {
            return "TIMESTAMP";
        } else if (javaObjectClass.isAssignableFrom(java.sql.Blob.class)) {
            return "BLOB";
        } else if (javaObjectClass.isAssignableFrom(java.sql.Clob.class)) {
            return "CLOB";
        } else if (javaObjectClass.isAssignableFrom(java.sql.Array.class)) {
            return "ARRAY";
        } else if (javaObjectClass.isAssignableFrom(java.sql.Struct.class)) {
            return "STRUCT";
        } else if (javaObjectClass.isAssignableFrom(java.sql.Ref.class)) {
            return "REF";
        } else if (javaObjectClass.isAssignableFrom(java.sql.SQLXML.class)) {
            return "SQLXML";
        } else if (javaObjectClass.isAssignableFrom(java.sql.NClob.class)) {
            return "NCLOB";
        } else if (javaObjectClass.isAssignableFrom(java.sql.RowId.class)) {
            return "ROWID";
        }

        throw new IllegalArgumentException("Unsupported Java-Type: " + javaObjectClass.getName());
    }

    /**
     * Get the Table from a class Entity.
     *
     * @param entity the Entity.
     * @return {@link String} as the Table.
     */
    public static String getTable(Class<?> entity) {
        if (!entity.isAnnotationPresent(Table.class)) {
            return null;
        }

        Table table = entity.getAnnotation(Table.class);
        if (table != null) {
            return table.name();
        }

        throw new IllegalArgumentException("No Table annotation found for class: " + entity.getName());
    }

    public static List<SQLParameter> getAllSQLParameter(Class<?> entity) {
        List<SQLParameter> parameters = new ArrayList<>();

        for (Field field : entity.getDeclaredFields()) {
            if (field.isAnnotationPresent(Property.class)) {
                Property property = field.getAnnotation(Property.class);
                parameters.add(new SQLParameter(property.name().toUpperCase(), field.getType()));
            }
        }

        if (entity.getSuperclass() != null && !entity.getSuperclass().isInstance(SQLEntity.class)) {
            for (Field field : entity.getSuperclass().getDeclaredFields()) {
                if (field.isAnnotationPresent(Property.class)) {
                    Property property = field.getAnnotation(Property.class);
                    parameters.add(new SQLParameter(property.name().toUpperCase(), field.getType()));
                }
            }
        }

        return parameters;
    }
}
