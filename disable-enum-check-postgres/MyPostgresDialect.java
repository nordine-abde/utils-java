import org.hibernate.dialect.PostgreSQLDialect;

/**
 * Postgres dialect default behavior is to create a check constraint for enum columns.
 * This class overrides this behavior to avoid this constraint creation.
 */

public class MyPostgresDialect extends PostgreSQLDialect {

    @Override
    public String getCheckCondition(String columnName, Class<? extends Enum<?>> enumType) {
        return null;
    }

}