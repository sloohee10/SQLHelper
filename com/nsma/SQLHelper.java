package com.nsma;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 *
 * @author Saleh Haddawi
 */
/**
 * SQLHelper is designed to make quick and easy access to database to perform
 * simple operations.
 *
 * @author Saleh Haddawi
 * @version 0.7
 */
public class SQLHelper implements AutoCloseable {

    //the connection to the databse
    private Connection connection = null;

    //data base URL
    private String DB_URL = "";

    private DatabaseType currentConnectionDatabaseType = globalDatabaseType;

    private TransactionManager transactionManager;

    private TableManager tablesManager;

    private KeyValueTable keyValueTable;

    private ConnectionManager connectionManager;

    private SQLHelperOperation sqlHelperOperation;

    private static DatabaseType globalDatabaseType = DatabaseType.AUTO;

    // -------------------------------------------- STATIC METHODS -------------------------------------------------------- \\
    public static void setDefinedDatabaseType(DatabaseType type) {
        globalDatabaseType = type;
    }

    public static DatabaseType getDefinedDatabaseType() {
        return globalDatabaseType;
    }

    private static String getColumns(String tableName, Connection connection) throws Exception {
        StringBuilder colNames = new StringBuilder(32);
        ResultSet rs = connection.prepareStatement("SELECT * FROM " + tableName).executeQuery();
        ResultSetMetaData rsmd = rs.getMetaData();
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            colNames.append(rsmd.getColumnName(i));
            if (i != rsmd.getColumnCount()) {
                colNames.append(",");
            }
        }

        return colNames.toString();
    }

    private static String getColumns(String url, String tableName, PreparedStatement ps) throws Exception {

        StringBuilder colNames = new StringBuilder(32);

        ResultSetMetaData psMetaData = ps.getMetaData();

        int colCount = psMetaData.getColumnCount();

        for (int i = 0; i < colCount; i++) {
            colNames.append(psMetaData.getColumnLabel(i + 1)).append(i + 1 == colCount ? "" : ",");
        }

        return colNames.toString();
    }

    private static boolean isTableExists(String table, Connection connection) throws Exception {

        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM " + table)) {
            try (ResultSet rs = ps.executeQuery()) {
            }
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    // -------------------------------------------- CONSTRUCTORS -------------------------------------------------------- \\
    /**
     * Open connection to the data base with provided URL. This method is used
     * for a connection without authentication.
     *
     * @param URL databse url can't be null or empty.
     *
     * @throws NullPointerException if URL is null.
     * @throws SQLHelperException if URL is empty.
     *
     */
    public SQLHelper(String URL) throws Exception {
        if (URL == null) {
            throw new NullPointerException("Database URL is null");
        }
        if (URL.trim().isEmpty()) {
            throw new SQLHelperException("Database URL is empty");
        }

        if (connection != null && !connection.isClosed()) {
            connection.close();
        }

        connection = null;

        DB_URL = URL;

        connection = DriverManager.getConnection(DB_URL);

        try {
            fetchDatabaseTypeFromConnectionMetaData();
        } catch (Throwable e) {
        }
    }

    /**
     * Open connection to the data base with provided URL and properties.
     *
     * @param URL databse url can't be null or empty.
     * @param properties database connection properties can't be null.
     *
     * @throws NullPointerException if URL or properties are null.
     * @throws SQLHelperException if URL is empty.
     *
     */
    public SQLHelper(String URL, Properties properties) throws Exception {
        if (URL == null) {
            throw new SQLHelperException("Database URL is null");
        }

        if (URL.trim().isEmpty()) {
            throw new SQLHelperException("Database URL is empty");
        }

        if (properties == null) {
            throw new SQLHelperException("Database properties is null");
        }

        if (connection != null && !connection.isClosed()) {
            connection.close();
        }

        connection = null;

        DB_URL = URL;

        connection = DriverManager.getConnection(DB_URL, properties);

        try {
            fetchDatabaseTypeFromConnectionMetaData();
        } catch (Throwable e) {
        }
    }

    /**
     * Open connection to the data base with provided URL,username and password.
     *
     * @param URL databse url can't be null or empty.
     * @param username database username.
     * @param password database password.
     *
     * @throws NullPointerException if URL is null.
     * @throws SQLHelperException if URL is empty.
     */
    public SQLHelper(String URL, String username, String password) throws Exception {
        if (URL == null) {
            throw new SQLHelperException("Database URL is null");
        }

        if (URL.trim().isEmpty()) {
            throw new SQLHelperException("Database URL is empty");
        }

        if (connection != null && !connection.isClosed()) {
            connection.close();
        }

        connection = null;

        DB_URL = URL;

        connection = DriverManager.getConnection(DB_URL, username, password);

        try {
            fetchDatabaseTypeFromConnectionMetaData();
        } catch (Throwable e) {
        }
    }

    // -------------------------------------------- PUBLIC METHODS -------------------------------------------------------- \\
    /**
     * close connection to the database and release any resources.
     * <br> This method has no effect if connection is already closed or hasn't
     * been started.
     *
     * @throws SQLException if a database access error occurs.
     *
     */
    @Override
    public void close() throws Exception {
        if (connection != null && !connection.isClosed()) {
            if (!connection.getAutoCommit()) {
                connection.rollback();
            }
            connection.close();
        }
    }

    public SQLHelperOperation op() {
        if (sqlHelperOperation == null) {
            sqlHelperOperation = new SQLHelperOperationImplementation(connection, currentConnectionDatabaseType);
        }

        return sqlHelperOperation;
    }

    public TableManager getTableManager() {
        if (tablesManager == null) {
            tablesManager = new TableManagerImplementation(connection);
        }

        return tablesManager;
    }

    public TransactionManager getTransactionManager() {
        if (transactionManager == null) {
            transactionManager = new TransactionManagerImplementation(connection);
        }

        return transactionManager;
    }

    public KeyValueTable getKeyValueTable() {
        if (keyValueTable == null) {
            keyValueTable = new KeyValueTableImplementation(connection);
        }

        return keyValueTable;
    }

    public ConnectionManager getConnectionManager() {
        if (connectionManager == null) {
            connectionManager = new ConnectionManagerImplementation(connection, DB_URL, currentConnectionDatabaseType);
        }

        return connectionManager;
    }

    // -------------------------------------------- PRIVATE METHODS -------------------------------------------------------- \\
    private void fetchDatabaseTypeFromConnectionMetaData() throws SQLException {
        if (globalDatabaseType != DatabaseType.AUTO) {
            currentConnectionDatabaseType = globalDatabaseType;
            return;
        }

        DatabaseMetaData metaData = connection.getMetaData();
        String databseName = metaData.getDatabaseProductName().toUpperCase();

        if (databseName.contains("MYSQL")) {
            currentConnectionDatabaseType = DatabaseType.MYSQL;
        } else if (databseName.contains("SQLITE")) {
            currentConnectionDatabaseType = DatabaseType.SQLITE;
        } else if (databseName.contains("ORACLE")) {
            currentConnectionDatabaseType = DatabaseType.ORACLE;
        } else if (databseName.contains("MICROSOFT ACCESS")) {
            currentConnectionDatabaseType = DatabaseType.MSACCESS;
        } else {
            currentConnectionDatabaseType = DatabaseType.OTHER;
        }
    }

    private static String Q_Marks(int size) {
        StringBuilder res = new StringBuilder(size * 2);
        for (int i = 0; i < size; i++) {
            res.append((i == size - 1) ? "?" : "?,");
        }
        return res.toString();
    }

    private static String Q_Marks(int size, String columnsName) {
        StringBuilder res = new StringBuilder(size * 5);
        String[] cn = columnsName.split(",");
        int columnsSize = cn.length;
        for (int i = 0; i < size && i < columnsSize; i++) {
            res.append(cn[i]).append(" = ?").append(i == size - 1 || i == columnsSize - 1 ? "" : ",");
        }
        return res.toString();
    }

    private static void setValuesForPreparedStatment(PreparedStatement ps, Object obj, int index) throws SQLException {
        if (obj instanceof String) {
            ps.setString(index, (String) obj);
        } else if (obj instanceof Integer) {
            ps.setInt(index, (int) obj);
        } else if (obj instanceof Double) {
            ps.setDouble(index, (double) obj);
        } else if (obj instanceof Byte[]) {
            ps.setBytes(index, (byte[]) obj);
        } else if (obj instanceof Long) {
            ps.setLong(index, (long) obj);
        } else if (obj instanceof Float) {
            ps.setFloat(index, (float) obj);
        } else if (obj instanceof Boolean) {
            ps.setBoolean(index, (boolean) obj);
        } else if (obj instanceof Byte) {
            ps.setByte(index, (byte) obj);
        } else if (obj instanceof Short) {
            ps.setShort(index, (short) obj);
        } else {
            ps.setObject(index, obj);
        }
    }

    private static <T> T castValue(Object obj, Class<T> clazz) throws SQLException {
        Object res = null;
        String objString = String.valueOf(obj).trim();
        if (obj == null) {
            if (clazz.isPrimitive()) {
                return getDefaultValue(clazz);
            }
            return null;
        } else if (clazz.equals(boolean.class) || clazz.equals(Boolean.class)) {
            res = objString.equalsIgnoreCase("true") || objString.equalsIgnoreCase("1");
        } else if (clazz.equals(String.class) || clazz.equals(Double.class)) {
            res = objString;
        } else if (clazz.equals(double.class) || clazz.equals(Double.class)) {
            res = (Double.parseDouble(objString));
        } else if (clazz.equals(int.class) || clazz.equals(Integer.class)) {
            res = (((int) Double.parseDouble(objString)));
        } else if (clazz.equals(long.class) || clazz.equals(Long.class)) {
            res = (((long) Double.parseDouble(objString)));
        } else if (clazz.equals(float.class) || clazz.equals(Float.class)) {
            res = (((float) Double.parseDouble(objString)));
        } else if (clazz.equals(short.class) || clazz.equals(Short.class)) {
            res = (((short) Double.parseDouble(objString)));
        } else if (clazz.equals(byte.class) || clazz.equals(Byte.class)) {
            res = (((byte) Double.parseDouble(objString)));
        } else if (clazz.equals(char.class) || clazz.equals(Character.class)) {
            res = (((char) objString.charAt(0)));
        } else {
            res = clazz.cast(obj);
        }
        T _res = (T) res;

        return _res;
    }

    private static void getTablesAndColumns(String url, String databaseName, Connection connection) throws Exception {
        DatabaseMetaData databaseMetaData = connection.getMetaData();

        try (ResultSet columns = databaseMetaData.getTables(databaseName, null, "%", new String[]{"TABLE"})) {
            while (columns.next()) {
                String tableName = columns.getString("TABLE_NAME");
                getColumns(tableName, connection);
            }
        }
    }

    private static <T> T createInstance(Class<T> objectClass) throws Exception {
        Constructor<?> constructor = objectClass.getConstructors()[0];
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] parameters = new Object[parameterTypes.length];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = getDefaultValue(parameterTypes[i]);
        }
        return (T) constructor.newInstance(parameters);
    }

    private static <T> T createConstructorAndNewInstance(Class<T> objectClass) throws Exception {

        Constructor constructor = objectClass.getConstructors()[0];
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] parameters = new Object[parameterTypes.length];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = getDefaultValue(parameterTypes[i]);
        }

        return (T) constructor.newInstance(parameters);
    }

    private static <T> T getDefaultValue(Class<T> clazz) {
        T def = (T) Array.get(Array.newInstance(clazz, 1), 0);
        return def;
    }

    public static enum DatabaseType {
        MYSQL, SQLITE, MSACCESS, ORACLE, AUTO, OTHER
    }

    // -------------------------------------------- PRIVATE CLASSES -------------------------------------------------------- \\
    private static class SQLHelperInsertStatmentImplementation implements SQLHelperInsertStatment {

        String table;
        String columns = null;

        Object[] valuesArray = null;
        SQLHelperValue valueNew = null;
        Map<String, Object> valuesMap = null;

        Connection connection;

        public SQLHelperInsertStatmentImplementation(Connection conn, String table) {
            this.connection = conn;
            this.table = table;
        }

        @Override
        public SQLHelperInsertStatment setCols(String columns) {
            if (columns != null && !columns.trim().isEmpty()) {
                this.columns = columns;
            }
            return this;
        }

        @Override
        public SQLHelperInsertStatment setValues(Object... values) {
            this.valuesArray = values;

            valuesMap = null;
            valueNew = null;

            return this;
        }

        @Override
        public SQLHelperInsertStatment setCol(String col, Object value) throws Exception {
            if (col == null) {
                throw new NullPointerException("column is null.");
            } else if (col.trim().isEmpty()) {
                throw new IllegalArgumentException("column is empty.");
            }
            if (valuesMap == null) {
                valuesMap = new HashMap<>();
            }
            valuesMap.put(col.toLowerCase().trim(), value);

            valuesArray = null;
            valueNew = null;

            return this;
        }

        @Override
        public SQLHelperInsertStatment setValues(SQLHelperValue valueObject) throws Exception {
            valueNew = valueObject;

            valuesArray = null;
            valuesMap = null;
            return this;
        }

        @Override
        public SQLHelperInsertStatment reset() {
            columns = null;
            valuesArray = null;
            valuesMap = null;
            valueNew = null;

            return this;
        }

        @Override
        public SQLHelperStatmentMetaData getMetaData() throws Exception {
            SQLHelperStatmentMetaDataImplementation metaData = new SQLHelperStatmentMetaDataImplementation();
            updateMetaData(metaData);
            return metaData;
        }

        @Override
        public int execute() throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            int res = 0;
            int size = 0;
            List<String> cols = null;

            if (valuesArray == null && valueNew == null && valuesMap != null) {
                StringBuilder columnBuilder = new StringBuilder(valuesMap.size() * 6);
                cols = new ArrayList<>(valuesMap.size());
                Iterator<String> iterator = valuesMap.keySet().iterator();
                while (iterator.hasNext()) {
                    String next = iterator.next();
                    columnBuilder.append(iterator.hasNext() ? next + "," : next);
                    cols.add(next);
                    size++;
                }
                columns = columnBuilder.toString();
            } else {
                if ((columns == null || columns.trim().isEmpty())) {
                    columns = getColumns(table, connection);
                }
                cols = new ArrayList<>();
                StringTokenizer st = new StringTokenizer(columns, ",", true);
                while (st.hasMoreElements()) {
                    String next = st.nextToken();
                    if (!next.equals(",")) {
                        cols.add(next);
                    } else {
                        size++;
                    }
                }
                size++;
            }

            StringBuilder sql = new StringBuilder(50);

            sql.append("INSERT INTO ").append(table).append("(").append(columns).append(") VALUES (").append(Q_Marks(size)).append(")");

            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {

                int varags = ps.getParameterMetaData().getParameterCount();

                int i = 0;
                if (valuesArray != null) {
                    for (; i < varags && i < valuesArray.length; i++) {
                        setValuesForPreparedStatment(ps, this.valuesArray[i], i + 1);
                    }
                }
                if (valuesMap != null) {
                    for (; i < varags && i < valuesMap.size(); i++) {
                        setValuesForPreparedStatment(ps, valuesMap.get(cols.get(i)), i + 1);
                    }
                }
                if (valueNew != null) {
                    for (; i < varags && i < size; i++) {
                        valueNew.getSQLHelperValue(cols.get(i).toLowerCase(), i + 1, ps);
                    }
                }

                if (i < varags) {
                    throw new SQLHelperException("SQL INSERT statement requires (" + varags + ") values but found (" + i + ") values, for columns: (" + columns + ")");
                }

                reset();
                res = ps.executeUpdate();
            }

            return res;
        }

        private void updateMetaData(SQLHelperStatmentMetaDataImplementation meta) throws Exception {
            if (meta == null) {
                return;
            }

            int size = 0;
            //List<String> cols = null;
            String _columns = null;

            if (valuesArray == null && valueNew == null && valuesMap != null) {
                StringBuilder columnBuilder = new StringBuilder(valuesMap.size() * 6);
                //cols = new ArrayList<>(valuesMap.size());
                Iterator<String> iterator = valuesMap.keySet().iterator();
                while (iterator.hasNext()) {
                    String next = iterator.next();
                    columnBuilder.append(iterator.hasNext() ? next + "," : next);
                    //cols.add(next);
                    size++;
                }
                _columns = columnBuilder.toString();
            } else {
                if ((_columns == null || _columns.trim().isEmpty())) {
                    _columns = getColumns(table, connection);
                }
                //cols = new ArrayList<>();
                StringTokenizer st = new StringTokenizer(_columns, ",", true);
                while (st.hasMoreElements()) {
                    String next = st.nextToken();
                    if (!next.equals(",")) {
                        //cols.add(next);
                    } else {
                        size++;
                    }
                }
                size++;
            }

            StringBuilder sql = new StringBuilder(50);

            if (size >= 1) {

                sql.append("INSERT INTO ").append(table).append("(").append(_columns).append(") VALUES (").append(Q_Marks(size)).append(")");

                meta.objects[0] = sql.toString();
                meta.objects[1] = _columns;
            } else {
                meta.objects[0] = null;
                meta.objects[1] = null;
            }

            meta.objects[2] = null;
            meta.objects[3] = null;

            meta.objects[4] = table;
            meta.objects[5] = getValues(_columns);
            meta.objects[6] = null;
            meta.objects[7] = null;
            meta.objects[8] = size;
        }

        private Object[] getValues(String columns) {
            Object[] result = null;
            if (valuesArray != null) {
                result = new Object[valuesArray.length];
                System.arraycopy(valuesArray, 0, result, 0, valuesArray.length);
            } else if (valuesMap != null) {
                result = new Object[valuesMap.size()];
                int index = 0;
                StringTokenizer st = new StringTokenizer(columns, ",", true);
                while (st.hasMoreElements()) {
                    String next = st.nextToken();
                    if (!next.equals(",")) {
                        //cols.add(next);
                        result[index++] = valuesMap.get(next);
                    }
                }
            }
            return result;
        }
    }

    private static class SQLHelperUpdateStatmentImplementation implements SQLHelperUpdateStatment {

        String table;
        String columns = null;
        String condition = null;
        Object[] valuesArray = null;
        Object[] conditionValues = null;

        Map<String, Object> valuesMap = null;

        SQLHelperValue valueNew = null;

        Connection connection;

        public SQLHelperUpdateStatmentImplementation(Connection conn, String table) {
            this.connection = conn;
            this.table = table;
        }

        @Override
        public SQLHelperUpdateStatment setCols(String columns) {
            this.columns = columns;

            return this;
        }

        @Override
        public SQLHelperUpdateStatment setValues(Object... values) {
            this.valuesArray = values;
            valuesMap = null;
            valueNew = null;

            return this;
        }

        @Override
        public SQLHelperUpdateStatment setValues(SQLHelperValue valueObject) {
            valueNew = valueObject;

            return this;
        }

        @Override
        public SQLHelperUpdateStatment setCol(String col, Object value) throws Exception {
            if (col == null) {
                throw new NullPointerException("column is null.");
            } else if (col.trim().isEmpty()) {
                throw new IllegalArgumentException("column is empty.");
            }
            if (valuesMap == null) {
                valuesMap = new HashMap<>();
            }
            valuesMap.put(col.toLowerCase().trim(), value);

            valuesArray = null;
            valueNew = null;

            return this;
        }

        @Override
        public SQLHelperUpdateStatment where(String condition, Object... values) {
            if (condition != null && !condition.trim().isEmpty()) {
                this.condition = "WHERE " + condition;
                this.conditionValues = values;
            }
            return this;
        }

        @Override
        public SQLHelperUpdateStatment reset() {
            columns = null;
            condition = null;
            valuesArray = null;
            conditionValues = null;
            valuesMap = null;
            valueNew = null;

            return this;
        }

        @Override
        public SQLHelperStatmentMetaData getMetaData() {
            SQLHelperStatmentMetaDataImplementation metaData = new SQLHelperStatmentMetaDataImplementation();
            updateMetaData(metaData);

            return metaData;
        }

        @Override
        public int execute() throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            int res = 0;
            int size = 0;
            List<String> cols = null;

            if (valuesArray == null && valueNew == null && valuesMap != null) {
                StringBuilder columnBuilder = new StringBuilder(valuesMap.size() * 6);
                cols = new ArrayList<>(valuesMap.size());
                Iterator<String> iterator = valuesMap.keySet().iterator();
                while (iterator.hasNext()) {
                    String next = iterator.next();
                    columnBuilder.append(iterator.hasNext() ? next + "," : next);
                    cols.add(next);
                    size++;
                }
                columns = columnBuilder.toString();
            } else {
                if ((columns == null || columns.trim().isEmpty())) {
                    columns = getColumns(table, connection);
                }
                cols = new ArrayList<>();
                StringTokenizer st = new StringTokenizer(columns, ",", true);
                while (st.hasMoreElements()) {
                    String next = st.nextToken();
                    if (!next.equals(",")) {
                        cols.add(next);
                    } else {
                        size++;
                    }
                }
                size++;
            }

            StringBuilder sql = new StringBuilder(50);

            sql.append("UPDATE ").append(table).append(" SET ").append(Q_Marks(size, columns)).append(" ").append(condition == null ? "" : condition);

            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {

                int varags = ps.getParameterMetaData().getParameterCount();

                int i = 0;

                if (valuesArray != null) {
                    for (; i < varags && i < valuesArray.length && i < size; i++) {
                        setValuesForPreparedStatment(ps, this.valuesArray[i], i + 1);
                    }
                }
                if (valuesMap != null) {
                    for (; i < varags && i < valuesMap.size() && i < size; i++) {
                        setValuesForPreparedStatment(ps, valuesMap.get(cols.get(i)), i + 1);
                    }
                }
                if (valueNew != null) {
                    for (; i < varags && i < size; i++) {
                        valueNew.getSQLHelperValue(cols.get(i).toLowerCase(), i + 1, ps);
                    }
                }

                if (conditionValues != null) {
                    for (int j = 0; i < varags && j < conditionValues.length; i++, j++) {
                        setValuesForPreparedStatment(ps, this.conditionValues[j], i + 1);
                    }
                }

                if (i < varags) {
                    throw new SQLHelperException("SQL UPDATE statement requires (" + varags + ") values but found (" + i + ") values, for columns: (" + columns + ")");
                }
                reset();

                res = ps.executeUpdate();
            }
            return res;
        }

        @Override
        public long executeLarge() throws Exception {if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            long res = 0;
            int size = 0;
            List<String> cols = null;

            if (valuesArray == null && valueNew == null && valuesMap != null) {
                StringBuilder columnBuilder = new StringBuilder(valuesMap.size() * 6);
                cols = new ArrayList<>(valuesMap.size());
                Iterator<String> iterator = valuesMap.keySet().iterator();
                while (iterator.hasNext()) {
                    String next = iterator.next();
                    columnBuilder.append(iterator.hasNext() ? next + "," : next);
                    cols.add(next);
                    size++;
                }
                columns = columnBuilder.toString();
            } else {
                if ((columns == null || columns.trim().isEmpty())) {
                    columns = getColumns(table, connection);
                }
                cols = new ArrayList<>();
                StringTokenizer st = new StringTokenizer(columns, ",", true);
                while (st.hasMoreElements()) {
                    String next = st.nextToken();
                    if (!next.equals(",")) {
                        cols.add(next);
                    } else {
                        size++;
                    }
                }
                size++;
            }

            StringBuilder sql = new StringBuilder(50);

            sql.append("UPDATE ").append(table).append(" SET ").append(Q_Marks(size, columns)).append(" ").append(condition == null ? "" : condition);

            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {

                int varags = ps.getParameterMetaData().getParameterCount();

                int i = 0;

                if (valuesArray != null) {
                    for (; i < varags && i < valuesArray.length && i < size; i++) {
                        setValuesForPreparedStatment(ps, this.valuesArray[i], i + 1);
                    }
                }
                if (valuesMap != null) {
                    for (; i < varags && i < valuesMap.size() && i < size; i++) {
                        setValuesForPreparedStatment(ps, valuesMap.get(cols.get(i)), i + 1);
                    }
                }
                if (valueNew != null) {
                    for (; i < varags && i < size; i++) {
                        valueNew.getSQLHelperValue(cols.get(i).toLowerCase(), i + 1, ps);
                    }
                }

                if (conditionValues != null) {
                    for (int j = 0; i < varags && j < conditionValues.length; i++, j++) {
                        setValuesForPreparedStatment(ps, this.conditionValues[j], i + 1);
                    }
                }

                if (i < varags) {
                    throw new SQLHelperException("SQL UPDATE statement requires (" + varags + ") values but found (" + i + ") values, for columns: (" + columns + ")");
                }
                reset();

                res = ps.executeLargeUpdate();
            }
            return res;
        }

        private void updateMetaData(SQLHelperStatmentMetaDataImplementation meta) {
            if (meta == null) {
                return;
            }

            String[] cols;
            if (valuesArray == null && valueNew == null && valuesMap != null) {
                StringBuilder columnBuilder = new StringBuilder(valuesMap.size() * 6);
                cols = new String[valuesMap.size()];
                Iterator<String> iterator = valuesMap.keySet().iterator();
                int i = 0;
                while (iterator.hasNext()) {
                    String next = iterator.next();
                    columnBuilder.append(iterator.hasNext() ? next + "," : next);
                    cols[i++] = next;
                }
                columns = columnBuilder.toString();
            } else {
                if ((columns == null || columns.trim().isEmpty())) {
                    try {
                        columns = getColumns(table, connection);
                    } catch (Exception ex) {
                        columns = null;
                    }
                }
                cols = columns == null ? null : columns.split(",");
            }

            StringBuilder sql = new StringBuilder(50);

            int size = 0;

            if (cols != null) {
                size = cols.length;

                sql.append("UPDATE ").append(table).append(" SET ").append(Q_Marks(size, columns)).append(" ").append(condition == null ? "" : condition);

                meta.objects[0] = sql.toString();
                meta.objects[1] = columns;
            } else {
                meta.objects[0] = null;
                meta.objects[1] = null;
            }

            meta.objects[2] = condition;
            meta.objects[3] = null;

            meta.objects[4] = table;

            meta.objects[5] = getValues(columns);
            meta.objects[6] = conditionValues;
            meta.objects[7] = null;

            meta.objects[8] = getReqValuesCount(sql.toString());
        }

        private Object[] getValues(String columns) {Object[] result = null;
            if (valuesArray != null) {
                result = new Object[valuesArray.length];
                System.arraycopy(valuesArray, 0, result, 0, valuesArray.length);
            } else if (valuesMap != null) {
                result = new Object[valuesMap.size()];
                int index = 0;
                StringTokenizer st = new StringTokenizer(columns, ",", true);
                while (st.hasMoreElements()) {
                    String next = st.nextToken();
                    if (!next.equals(",")) {
                        //cols.add(next);
                        result[index++] = valuesMap.get(next);
                    }
                }
            }
            return result;
        }

        private int getReqValuesCount(String sql) {
            int result = 0;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                result = ps.getParameterMetaData().getParameterCount();
            } catch (Throwable t) {
                result = -1;
            }
            return result;
        }
    }

    private static class SQLHelperDeleteStatmentImplementation implements SQLHelperDeleteStatment {

        String table;
        Connection connection;

        String condition;
        Object[] conditionValues;

        public SQLHelperDeleteStatmentImplementation(Connection conn, String table) {
            this.connection = conn;
            this.table = table;
        }

        @Override
        public SQLHelperDeleteStatment where(String condition, Object... values) {
            if (condition != null && !condition.trim().isEmpty()) {
                this.condition = "WHERE " + condition;
                this.conditionValues = values;
            }
            return this;
        }

        @Override
        public SQLHelperDeleteStatment reset() {
            condition = null;
            conditionValues = null;

            return this;
        }

        @Override
        public SQLHelperStatmentMetaData getMetaData() {
            SQLHelperStatmentMetaDataImplementation metaData = new SQLHelperStatmentMetaDataImplementation();
            updateMetaData(metaData);
            return metaData;
        }

        @Override
        public int execute() throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            int res = 0;

            StringBuilder sql = new StringBuilder(50);

            sql.append("DELETE FROM ").append(table).append(" ").append(condition == null ? "" : condition);

            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {

                int argsCount = ps.getParameterMetaData().getParameterCount();

                if (this.conditionValues != null && this.conditionValues.length < argsCount) {
                    throw new SQLHelperException("SQL DELETE statement requires (" + argsCount + ") values but found (" + (this.conditionValues == null ? 0 : this.conditionValues.length) + ") values.");
                }

                if (conditionValues != null) {
                    for (int i = 0; i < conditionValues.length; i++) {
                        setValuesForPreparedStatment(ps, conditionValues[i], i + 1);
                    }
                }
                reset();

                res = ps.executeUpdate();
            }

            return res;
        }

        @Override
        public long executeLarge() throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            long res = 0;

            StringBuilder sql = new StringBuilder(50);

            sql.append("DELETE FROM ").append(table).append(" ").append(condition == null ? "" : condition);

            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {

                int argsCount = ps.getParameterMetaData().getParameterCount();

                if (this.conditionValues != null && this.conditionValues.length < argsCount) {
                    throw new SQLHelperException("SQL DELETE statement requires (" + argsCount + ") values but found (" + (this.conditionValues == null ? 0 : this.conditionValues.length) + ") values.");
                }

                if (conditionValues != null) {
                    for (int i = 0; i < conditionValues.length; i++) {
                        setValuesForPreparedStatment(ps, conditionValues[i], i + 1);
                    }
                }
                reset();

                res = ps.executeLargeUpdate();
            }

            return res;
        }

        private void updateMetaData(SQLHelperStatmentMetaDataImplementation meta) {
            if (meta == null) {
                return;
            }

            StringBuilder sql = new StringBuilder(50);

            sql.append("DELETE FROM ").append(table).append(" ").append(condition == null ? "" : condition);

            meta.objects[0] = sql.toString();
            meta.objects[1] = null;

            meta.objects[2] = condition;
            meta.objects[3] = null;

            meta.objects[4] = table;

            meta.objects[5] = null;
            meta.objects[6] = conditionValues;
            meta.objects[7] = null;

            meta.objects[8] = getReqValuesCount(sql.toString());
        }

        private int getReqValuesCount(String sql) {
            int result = 0;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                result = ps.getParameterMetaData().getParameterCount();
            } catch (Throwable t) {

            }
            return result;
        }
    }

    private static class SQLHelperSelectStatmentImplementation implements SQLHelperSelectStatment {

        String table = null;
        String columns = null;
        String whereCondition = null;
        String groupBy = null;
        String having = null;
        String orderBY = null;

        boolean selectDistinct;

        Connection connection = null;

        int limitRows = 0;

        Object[] conditionValues = null;

        Object[] havingValues = null;

        boolean canDriverCastObjectToSpecificType = true;

        DatabaseType databaseType;

        private SQLHelperSelectStatmentImplementation(Connection connection, String tableName, DatabaseType databaseType) {
            this.connection = connection;
            this.table = tableName;
            this.databaseType = databaseType;
        }

        @Override
        public SQLHelperSelectStatment setCols(String columns) {
            if (columns != null && !columns.trim().isEmpty()) {
                this.columns = columns;
            }
            return this;
        }

        @Override
        public SQLHelperSelectStatment where(String condition, Object... values) {
            if (condition != null && !condition.trim().isEmpty()) {
                this.whereCondition = condition.toUpperCase().trim().startsWith("WHERE") ? condition : "WHERE " + condition;
                this.conditionValues = values;
            }
            return this;
        }

        @Override
        public SQLHelperSelectStatment having(String condition, Object... values) {
            if (condition != null && !condition.trim().isEmpty()) {
                this.having = condition.toUpperCase().trim().startsWith("HAVING") ? condition : "HAVING " + condition;
                this.havingValues = values;
            }
            return this;
        }

        @Override
        public SQLHelperSelectStatment distinct(boolean selectDistinct) {
            this.selectDistinct = selectDistinct;
            return this;
        }

        @Override
        public SQLHelperSelectStatment limit(int rows) {
            limitRows = rows;
            return this;
        }

        @Override
        public SQLHelperSelectStatment orderBy(String column) {
            if (column != null && !column.trim().isEmpty()) {
                orderBY = column;
            }
            return this;
        }

        @Override
        public SQLHelperSelectStatment groupBy(String column) {
            if (column != null && !column.trim().isEmpty()) {
                groupBy = column;
            }
            return this;
        }

        @Override
        public SQLHelperSelectStatment reset() {
            whereCondition = null;
            conditionValues = null;
            limitRows = 0;
            distinct(false);
            columns = null;
            orderBY = null;
            groupBy = null;
            having = null;

            return this;
        }

        @Override
        public double max(String column) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }
            if (column == null) {
                throw new NullPointerException("column is null in SQL MAX.");
            }
            if (column.trim().isEmpty()) {
                throw new IllegalArgumentException("column is empty in SQL MAX.");
            }
            double res = Double.NaN;
            StringBuilder sql = new StringBuilder(50);
            sql.append("SELECT ").append("MAX(").append(selectDistinct ? "DISTINCT " : "").append(column).append(") FROM ").append(table).append(" ").append(whereCondition == null ? "" : whereCondition);

            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {

                int argsCount = ps.getParameterMetaData().getParameterCount();

                if (this.conditionValues != null && this.conditionValues.length < argsCount) {
                    throw new SQLHelperException("SQL MAX() function in SELECT requires (" + argsCount + ") values but found (" + (this.conditionValues == null ? 0 : this.conditionValues.length) + ") values.");
                }

                if (conditionValues != null) {
                    for (int i = 0; i < conditionValues.length && i < argsCount; i++) {
                        setValuesForPreparedStatment(ps, conditionValues[i], i + 1);
                    }
                }

                reset();

                ResultSet rs = ps.executeQuery();
                if (rs.next()) {

                    double r = rs.getDouble(1);
                    if (!rs.wasNull()) {
                        rs.close();
                        res = r;
                    }
                }
                rs.close();
            }

            return res;
        }

        @Override
        public double min(String column) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }
            if (column == null) {
                throw new NullPointerException("column is null in SQL MIN.");
            }
            if (column.trim().isEmpty()) {
                throw new IllegalArgumentException("column is empty in SQL MIN.");
            }
            double res = Double.NaN;

            StringBuilder sql = new StringBuilder(50);
            sql.append("SELECT ").append("MIN(").append(selectDistinct ? "DISTINCT " : "").append(column).append(") FROM ").append(table).append(" ").append(whereCondition == null ? "" : whereCondition);

            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {

                int argsCount = ps.getParameterMetaData().getParameterCount();

                if (this.conditionValues != null && this.conditionValues.length < argsCount) {
                    throw new SQLHelperException("SQL MIN() function in SELECT requires (" + argsCount + ") values but found (" + (this.conditionValues == null ? 0 : this.conditionValues.length) + ") values.");
                }

                if (conditionValues != null) {
                    for (int i = 0; i < conditionValues.length && i < argsCount; i++) {
                        setValuesForPreparedStatment(ps, conditionValues[i], i + 1);
                    }
                }

                reset();

                ResultSet rs = ps.executeQuery();
                if (rs.next()) {

                    double r = rs.getDouble(1);
                    if (!rs.wasNull()) {
                        rs.close();
                        res = r;
                    }
                }
                rs.close();
            }

            return res;
        }

        @Override
        public double sum(String column) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }
            if (column == null) {
                throw new NullPointerException("column is null in SQL SUM.");
            }
            if (column.trim().isEmpty()) {
                throw new IllegalArgumentException("column is empty in SQL SUM.");
            }
            double res = Double.NaN;

            StringBuilder sql = new StringBuilder(50);
            sql.append("SELECT ").append("SUM(").append(selectDistinct ? "DISTINCT " : "").append(column).append(") FROM ").append(table).append(" ").append(whereCondition == null ? "" : whereCondition);

            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {

                int argsCount = ps.getParameterMetaData().getParameterCount();

                if (this.conditionValues != null && this.conditionValues.length < argsCount) {
                    throw new SQLHelperException("SQL SUM() function in SELECT requires (" + argsCount + ") values but found (" + (this.conditionValues == null ? 0 : this.conditionValues.length) + ") values.");
                }

                if (conditionValues != null) {
                    for (int i = 0; i < conditionValues.length && i < argsCount; i++) {
                        setValuesForPreparedStatment(ps, conditionValues[i], i + 1);
                    }
                }

                reset();

                ResultSet rs = ps.executeQuery();
                if (rs.next()) {

                    double r = rs.getDouble(1);
                    if (!rs.wasNull()) {
                        rs.close();
                        res = r;
                    }
                }
                rs.close();
            }

            return res;
        }

        @Override
        public double avg(String column) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }
            if (column == null) {
                throw new NullPointerException("column is null in SQL AVG.");
            }
            if (column.trim().isEmpty()) {
                throw new IllegalArgumentException("column is empty in SQL AVG.");
            }

            double res = Double.NaN;

            StringBuilder sql = new StringBuilder(50);
            sql.append("SELECT ").append("AVG(").append(selectDistinct ? "DISTINCT " : "").append(column).append(") FROM ").append(table).append(" ").append(whereCondition == null ? "" : whereCondition);

            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {

                int argsCount = ps.getParameterMetaData().getParameterCount();

                if (this.conditionValues != null && this.conditionValues.length < argsCount) {
                    throw new SQLHelperException("SQL AVG() function in SELECT requires (" + argsCount + ") values but found (" + (this.conditionValues == null ? 0 : this.conditionValues.length) + ") values.");
                }

                if (conditionValues != null) {
                    for (int i = 0; i < conditionValues.length && i < argsCount; i++) {
                        setValuesForPreparedStatment(ps, conditionValues[i], i + 1);
                    }
                }

                reset();

                ResultSet rs = ps.executeQuery();
                if (rs.next()) {

                    double r = rs.getDouble(1);
                    if (!rs.wasNull()) {
                        rs.close();
                        res = r;
                    }
                }
                rs.close();
            }

            return res;
        }

        @Override
        public long count(String column) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }
            if (column == null) {
                throw new NullPointerException("column is null in SQL COUNT.");
            }
            if (column.trim().isEmpty()) {
                throw new IllegalArgumentException("column is empty in SQL COUNT.");
            }
            if (column.trim().equals("*") && selectDistinct) {
                throw new SQLHelperException("DISTINCT can't be used with (*) in COUNT.");
            }

            long res = 0;

            StringBuilder sql = new StringBuilder(50);
            sql.append("SELECT (").append("COUNT(").append(selectDistinct ? "DISTINCT " : "").append(column).append(")").append(") FROM ").append(table).append(" ").append(whereCondition == null ? "" : whereCondition);
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {

                int argsCount = ps.getParameterMetaData().getParameterCount();

                if (this.conditionValues != null && this.conditionValues.length < argsCount) {
                    throw new SQLHelperException("SQL COUNT() function in SELECT requires (" + argsCount + ") values but found (" + (this.conditionValues == null ? 0 : this.conditionValues.length) + ") values.");
                }

                if (conditionValues != null) {
                    for (int i = 0; i < conditionValues.length && i < argsCount; i++) {
                        setValuesForPreparedStatment(ps, conditionValues[i], i + 1);
                    }
                }

                reset();

                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    res = rs.getLong(1);
                }
            }

            return res;
        }

        @Override
        public ResultSet execute() throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            StringBuilder sql = new StringBuilder(50);

            if (limitRows > 0 && databaseType == DatabaseType.ORACLE) {
                sql.append("SELECT * FROM ( ");
            }

            sql.append("SELECT ").append(limitRows > 0 && (databaseType == DatabaseType.MSACCESS) ? "TOP " + limitRows + " " : "")
                    .append(this.selectDistinct ? "DISTINCT " : "").append(columns == null ? "*" : columns).append(" FROM ").append(table).append(" ").append(whereCondition == null ? "" : whereCondition)
                    .append(groupBy == null ? "" : " GROUP BY ").append(groupBy == null ? "" : groupBy + " ").append(having == null ? "" : having).append(orderBY == null ? "" : " ORDER BY ").append(orderBY == null ? "" : orderBY + " ")
                    .append(limitRows > 0 && (databaseType == DatabaseType.MYSQL || databaseType == DatabaseType.SQLITE || databaseType == DatabaseType.OTHER) ? " LIMIT " + limitRows + " " : "");

            if (limitRows > 0 && databaseType == DatabaseType.ORACLE) {
                sql.append(" ) WHERE ROWNUM <= ").append(limitRows);
            }

            PreparedStatement ps = connection.prepareStatement(sql.toString());

            int argsCount = ps.getParameterMetaData().getParameterCount();

            if (this.conditionValues != null && this.conditionValues.length < argsCount) {
                throw new SQLHelperException("SQL SELECT statement requires (" + argsCount + ") values but found (" + (this.conditionValues == null ? 0 : this.conditionValues.length) + ") values.");
            }

            if (conditionValues != null) {
                for (int i = 0; i < conditionValues.length && i < argsCount; i++) {
                    setValuesForPreparedStatment(ps, conditionValues[i], i + 1);
                }
            }
            reset();

            ResultSet rs = ps.executeQuery();

            return rs;
        }

        @Override
        public <T extends SQLHelperValue> List<T> execute(Class<T> returnListType) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            StringBuilder sql = new StringBuilder(50);

            if (limitRows > 0 && databaseType == DatabaseType.ORACLE) {
                sql.append("SELECT * FROM ( ");
            }

            sql.append("SELECT ").append(limitRows > 0 && (databaseType == DatabaseType.MSACCESS) ? "TOP " + limitRows + " " : "")
                    .append(this.selectDistinct ? "DISTINCT " : "").append(columns == null ? "*" : columns).append(" FROM ").append(table).append(" ").append(whereCondition == null ? "" : whereCondition)
                    .append(groupBy == null ? "" : " GROUP BY ").append(groupBy == null ? "" : groupBy + " ").append(orderBY == null ? "" : " ORDER BY ").append(orderBY == null ? "" : orderBY + " ")
                    .append(limitRows > 0 && (databaseType == DatabaseType.MYSQL || databaseType == DatabaseType.SQLITE || databaseType == DatabaseType.OTHER) ? " LIMIT " + limitRows + " " : "");

            if (limitRows > 0 && databaseType == DatabaseType.ORACLE) {
                sql.append(" ) WHERE ROWNUM <= ").append(limitRows);
            }

            PreparedStatement ps = connection.prepareStatement(sql.toString());

            int argsCount = ps.getParameterMetaData().getParameterCount();

            if (this.conditionValues != null && this.conditionValues.length < argsCount) {
                throw new SQLHelperException("SQL SELECT statement requires (" + argsCount + ") values but found (" + (this.conditionValues == null ? 0 : this.conditionValues.length) + ") values.");
            }

            if (conditionValues != null) {
                for (int i = 0; i < conditionValues.length; i++) {
                    setValuesForPreparedStatment(ps, conditionValues[i], i + 1);
                }
            }

            List<T> resultList = new ArrayList<>();

            reset();

            ResultSet rs = ps.executeQuery();

            ResultSetMetaData rsMeta = rs.getMetaData();

            int colCount = rsMeta.getColumnCount();

            while (rs.next()) {

                T obj = createConstructorAndNewInstance(returnListType);

                for (int i = 0; i < colCount; i++) {
                    String colName = rsMeta.getColumnLabel(i + 1).toLowerCase();

                    obj.setSQLHelperValue(colName, rs);
                }
                resultList.add(obj);
            }
            rs.close();
            ps.close();

            return resultList;
        }
    }

    private static class SQLHelperStatmentMetaDataImplementation implements SQLHelperStatmentMetaData {

        // 0 -> SQLString
        // 1 -> columns
        // 2 -> whereString
        // 3 -> havingString
        // 4 -> table
        // 5 -> values
        // 6 -> where values
        // 7 -> having values
        // 8 -> required values count
        public Object[] objects = new Object[9];

        public SQLHelperStatmentMetaDataImplementation() {
            objects[8] = 0;
        }

        @Override
        public String getSQLString() throws Exception {
            return (String) objects[0];
        }

        @Override
        public String getColumns() throws Exception {
            return (String) objects[1];
        }

        @Override
        public String getWhereString() {
            return (String) objects[2];
        }

        @Override
        public String getHavingString() {
            return (String) objects[3];
        }

        @Override
        public String getTable() {
            return (String) objects[4];
        }

        @Override
        public Object[] getValues() {
            return (Object[]) objects[5];
        }

        @Override
        public Object[] getWhereValues() {
            return (Object[]) objects[6];
        }

        @Override
        public Object[] getHavingValues() {
            return (Object[]) objects[7];
        }

        @Override
        public int getRequiredValuesCount() throws Exception {
            return (int) objects[8];
        }

        @Override
        public Object[] getAllObjects() throws Exception {
            Object[] _objects = new Object[objects.length];

            System.arraycopy(objects, 0, _objects, 0, objects.length);

            return _objects;
        }
    }

    private static class TableManagerImplementation implements TableManager {

        Connection connection;

        public TableManagerImplementation(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void dropTable(String table) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            try (PreparedStatement ps = connection.prepareStatement("DROP TABLE " + table)) {
                ps.executeUpdate();
            }
        }

        @Override
        public void dropTableIfExists(String table) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            if (isTableExists(table)) {
                dropTable(table);
            }
        }

        @Override
        public void createTable(String table, String columnsNamesWithType) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            StringBuilder sqlS = new StringBuilder(50);
            sqlS.append("CREATE TABLE ").append(table).append(" ( ").append(columnsNamesWithType).append(" ) ");

            try (PreparedStatement ps = connection.prepareStatement(sqlS.toString())) {
                ps.executeUpdate();
            }
        }

        @Override
        public void createTableIfNotExists(String table, String columnsNamesWithType) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            if (!isTableExists(table)) {
                createTable(table, columnsNamesWithType);
            }
        }

        @Override
        public boolean isTableExists(String table) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            try {
                try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM " + table)) {
                    ResultSet rs = ps.executeQuery();
                    rs.close();
                }
                return true;
            } catch (Throwable e) {
                return false;
            }
        }

        @Override
        public void addColumn(String table, String columnWithType) throws Exception {
            try (PreparedStatement ps = connection.prepareStatement("ALTER TABLE " + table + " ADD " + columnWithType)) {
                ps.executeUpdate();
            }
        }

        @Override
        public void dropColumn(String table, String column) throws Exception {
            String columns = SQLHelper.getColumns(table, connection);
            if (!columns.toLowerCase().contains(column.toLowerCase())) {
                System.err.println("COLUMN NOT FOUND");
                return;
            }
            try (PreparedStatement ps = connection.prepareStatement("ALTER TABLE " + table + " DROP COLUMN " + column)) {
                ps.executeUpdate();
            } catch (Throwable t) {
                System.err.println("SSSSSSSS" + t);

                String[] cols = columns.split(",");
                String[] colsWithoutColumn = new String[cols.length - 1];
                String[] types = new String[colsWithoutColumn.length];
                int index = 0;

                for (String col : cols) {
                    if (!col.equalsIgnoreCase(column)) {
                        colsWithoutColumn[index++] = col;
                    }
                }

                try {
                    try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + table)) {
                        for (int i = 0; i < colsWithoutColumn.length; i++) {
                            types[i] = ps.getMetaData().getColumnTypeName(i + 1) + "(" + ps.getMetaData().getColumnDisplaySize(i + 1) + ")";
                        }
                    }

                    StringBuilder sql = new StringBuilder((colsWithoutColumn.length * 6) * 5);
                    StringBuilder sqlColumns = new StringBuilder(colsWithoutColumn.length * 6);
                    for (int i = 0; i < colsWithoutColumn.length; i++) {
                        if (i == colsWithoutColumn.length - 1) {
                            sql.append(colsWithoutColumn[i]).append(" ").append(types[i]);
                            sqlColumns.append(colsWithoutColumn[i]);
                        } else {
                            sql.append(colsWithoutColumn[i]).append(" ").append(types[i]).append(",");
                            sqlColumns.append(colsWithoutColumn[i]).append(",");
                        }
                    }

                    try (PreparedStatement ps = connection.prepareStatement("ALTER TABLE " + table + " RENAME TO " + table + "_sqlhelper_old")) {
                        ps.executeUpdate();
                    }

                    createTable(table, sql.toString());

                    System.out.println("INSERT INTO " + table + " (" + sqlColumns + ")" + " SELECT " + sqlColumns + " FROM " + table + "_sqlhelper_old");

                    try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + table + " (" + sqlColumns + ")" + " SELECT " + sqlColumns + " FROM " + table + "_sqlhelper_old")) {
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = connection.prepareStatement("DROP TABLE " + table + "_sqlhelper_old")) {
                        ps.executeUpdate();
                    }
                } catch (Throwable e) {
                    try {
                        if (isTableExists(table + "_sqlhelper_old")) {
                            dropTableIfExists(table);
                            try (PreparedStatement ps = connection.prepareStatement("ALTER TABLE " + table + "_sqlhelper_old" + " RENAME TO " + table)) {
                                ps.executeUpdate();
                            }
                        }

                        try (PreparedStatement ps = connection.prepareStatement("SELECT * INTO " + table + "_sqlhelper_old" + " FROM " + table)) {
                            ps.close();
                        }
                    } catch (Throwable e1) {
                        throw new SQLFeatureNotSupportedException("drop column is not supported.");
                    }
                }
            }
        }

        @Override
        public String getColumns(String table) throws Exception {
            return SQLHelper.getColumns(table, connection);
        }
    }

    private static class TransactionManagerImplementation implements TransactionManager {

        Connection connection;

        public TransactionManagerImplementation(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void begin() throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            if (connection.getAutoCommit()) {
                connection.setAutoCommit(false);
                connection.setSavepoint();
            }
        }

        @Override
        public void commit() throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            if (connection.getAutoCommit()) {
                throw new SQLHelperException("Can't commit while no transaction is going.");
            }

            connection.commit();
            connection.setAutoCommit(true);
        }

        @Override
        public void rollback() throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            if (connection.getAutoCommit()) {
                throw new SQLHelperException("Can't rollback while no transaction is going.");
            }

            connection.rollback();
            connection.setAutoCommit(true);
        }

        @Override
        public void rollback(Savepoint savepoint) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            if (connection.getAutoCommit()) {
                throw new SQLHelperException("Can't rollback while no transaction is going.");
            }

            connection.rollback(savepoint);
        }

        @Override
        public boolean isAutoCommit() throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            return connection.getAutoCommit();
        }

        @Override
        public Savepoint createSavePoint() throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            if (connection.getAutoCommit()) {
                throw new SQLHelperException("Can't create save point while no transaction is going.");
            }

            return connection.setSavepoint();
        }
    }

    private static class KeyValueTableImplementation implements KeyValueTable {

        Connection connection;
        String keyValueTableName = "sqlhelper_key_value_table";

        public KeyValueTableImplementation(Connection connection) {
            this.connection = connection;
        }

        @Override
        public String getKeyValueTableName() {
            return keyValueTableName;
        }

        @Override
        public void put(String key, Object value) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            if (key == null) {
                throw new SQLHelperException("key is null.");
            }

            int NUM_ROWS_EFFECTED;

            try {
                try (PreparedStatement ps = connection.prepareStatement("UPDATE " + keyValueTableName + " SET sqlhelper_value = ? WHERE sqlhelper_key = ?")) {
                    setValuesForPreparedStatment(ps, value, 1);
                    ps.setString(2, key);
                    NUM_ROWS_EFFECTED = ps.executeUpdate();
                }
                if (NUM_ROWS_EFFECTED == 0) {
                    try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + keyValueTableName + " (sqlhelper_key,sqlhelper_value) VALUES (?,?) ")) {
                        ps.setString(1, key);
                        setValuesForPreparedStatment(ps, value, 2);
                        ps.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                if (!isTableExists(keyValueTableName, connection)) {
                    try (PreparedStatement ps = connection.prepareStatement("CREATE TABLE " + keyValueTableName + " (sqlhelper_key VARCHAR(1024),sqlhelper_value VARCHAR(1024))")) {
                        ps.executeUpdate();
                    }

                    put(key, value);

                } else {
                    throw e;
                }
            }
        }

        @Override
        public Object get(String key) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            try (PreparedStatement ps = connection.prepareStatement("SELECT sqlhelper_value FROM " + keyValueTableName + " WHERE sqlhelper_key = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Object obj = rs.getObject("sqlhelper_value");
                        return obj;
                    } else {
                        throw new SQLHelperException("Unknown key '" + key + "'.");
                    }
                }
            } catch (Throwable e) {
                if (!isTableExists(keyValueTableName, connection)) {
                    try (PreparedStatement ps1 = connection.prepareStatement("CREATE TABLE " + keyValueTableName + " (sqlhelper_key VARCHAR(1024),sqlhelper_value VARCHAR(1024))")) {
                        ps1.executeUpdate();
                    }
                    throw new SQLHelperException("Unknown key '" + key + "'.");
                } else {
                    throw e;
                }
            }
        }

        @Override
        public <T> T get(String key, Class<T> resultType) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            T obj = null;
            try (PreparedStatement ps = connection.prepareStatement("SELECT sqlhelper_value FROM " + keyValueTableName + " WHERE sqlhelper_key = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        try {
                            obj = rs.getObject("sqlhelper_value", resultType);
                            if (!obj.getClass().equals(resultType)) {
                                obj = castValue(obj, resultType);
                            }
                        } catch (Throwable e) {
                            try {
                                obj = castValue(rs.getObject("sqlhelper_value"), resultType);

                            } catch (Throwable e1) {
                                throw new SQLHelperException("Error happened while getting value for key '" + key + "': " + e1);
                            }
                        }
                    } else {
                        throw new SQLHelperException("Unknown key '" + key + "'.");
                    }
                }
            } catch (Throwable t) {
                if (!isTableExists(keyValueTableName, connection)) {
                    try (PreparedStatement ps1 = connection.prepareStatement("CREATE TABLE " + keyValueTableName + " (sqlhelper_key VARCHAR(1024),sqlhelper_value VARCHAR(1024))")) {
                        ps1.executeUpdate();
                    }
                    throw new SQLHelperException("Unknown key '" + key + "'.");
                } else {
                    throw t;
                }
            }
            return obj;
        }

        @Override
        public boolean containsKey(String key) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(sqlhelper_key) FROM " + keyValueTableName + " WHERE sqlhelper_key = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            } catch (Throwable t) {
                if (!isTableExists(keyValueTableName, connection)) {
                    try (PreparedStatement ps1 = connection.prepareStatement("CREATE TABLE " + keyValueTableName + " (sqlhelper_key VARCHAR(1024),sqlhelper_value VARCHAR(1024))")) {
                        ps1.executeUpdate();
                    }
                    return false;
                } else {
                    throw t;
                }
            }
            return false;
        }

        @Override
        public String containsValue(Object value) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            String res = null;

            try (PreparedStatement ps = connection.prepareStatement("SELECT sqlhelper_key FROM " + keyValueTableName + " WHERE sqlhelper_value = ?")) {
                setValuesForPreparedStatment(ps, value, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        res = rs.getString(1);
                    }
                } catch (Throwable e) {
                    ps.setString(1, String.valueOf(value));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            res = rs.getString(1);
                        }
                    } catch (Throwable e1) {
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                res = rs.getString(1);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                if (!isTableExists(keyValueTableName, connection)) {
                    try (PreparedStatement ps1 = connection.prepareStatement("CREATE TABLE " + keyValueTableName + " (sqlhelper_key VARCHAR(1024),sqlhelper_value VARCHAR(1024))")) {
                        ps1.executeUpdate();
                    }
                    return null;
                } else {
                    throw t;
                }
            }
            return res;
        }

        @Override
        public Object remove(String key) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            Object value = null;
            if (containsKey(key)) {
                value = get(key);
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + keyValueTableName + " WHERE sqlhelper_key = ?")) {
                    ps.setString(1, key);
                    ps.executeUpdate();
                }
            }
            return value;
        }

        @Override
        public void clear() throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + keyValueTableName)) {
                ps.executeUpdate();
            } catch (Throwable t) {
                if (!isTableExists(keyValueTableName, connection)) {
                    try (PreparedStatement ps1 = connection.prepareStatement("CREATE TABLE " + keyValueTableName + " (sqlhelper_key VARCHAR(1024),sqlhelper_value VARCHAR(1024))")) {
                        ps1.executeUpdate();
                    }
                } else {
                    throw t;
                }
            }
        }

        @Override
        public int size() throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(sqlhelper_key) FROM " + keyValueTableName)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (Throwable t) {
                if (!isTableExists(keyValueTableName, connection)) {
                    try (PreparedStatement ps1 = connection.prepareStatement("CREATE TABLE " + keyValueTableName + " (sqlhelper_key VARCHAR(1024),sqlhelper_value VARCHAR(1024))")) {
                        ps1.executeUpdate();
                    }
                } else {
                    throw t;
                }
            }

            return 0;
        }

        @Override
        public Object replace(String key, Object newValue) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            Object value = null;
            if (containsKey(key)) {
                value = get(key);
                put(key, newValue);
            }
            return value;
        }

        @Override
        public Map<String, Object> getMap() throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            Map<String, Object> keyValueMap = new HashMap();
            try (PreparedStatement ps = connection.prepareStatement("SELECT sqlhelper_key,sqlhelper_value FROM " + keyValueTableName)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Object obj = rs.getObject(2);
                        keyValueMap.put(rs.getString(1), obj);
                    }
                }
            } catch (Throwable t) {
                if (!isTableExists(keyValueTableName, connection)) {
                    try (PreparedStatement ps1 = connection.prepareStatement("CREATE TABLE " + keyValueTableName + " (sqlhelper_key VARCHAR(1024),sqlhelper_value VARCHAR(1024))")) {
                        ps1.executeUpdate();
                    }
                } else {
                    throw t;
                }
            }
            return keyValueMap;
        }

        @Override
        public void putMap(Map<String, Object> map) throws Exception {
            if (connection == null || connection.isClosed()) {
                throw new SQLHelperException("No operations allowed after connection closed");
            }

            Iterator<String> keysIterator = map.keySet().iterator();

            while (keysIterator.hasNext()) {
                String key = keysIterator.next();
                put(key, map.get(key));
            }
        }
    }

    private static class ConnectionManagerImplementation implements ConnectionManager {

        Connection connection;
        String url;
        DatabaseType databaseType;

        public ConnectionManagerImplementation(Connection connection, String url, DatabaseType databaseType) {
            this.connection = connection;
            this.url = url;
            this.databaseType = databaseType;
        }

        @Override
        public Connection getConnection() {
            return connection;
        }

        @Override
        public String getDatabaseURL() {
            return url;
        }

        @Override
        public DatabaseType getDatabaseType() {
            return databaseType;
        }

        @Override
        public boolean isClosed() throws Exception {
            return connection == null || connection.isClosed();
        }

        @Override
        public void close() throws Exception {
            if (connection == null || !connection.isClosed()) {
                connection.close();
            }
        }

    }

    private static class SQLHelperOperationImplementation implements SQLHelperOperation {

        Connection connection;
        DatabaseType databaseType;

        public SQLHelperOperationImplementation(Connection connection, DatabaseType databaseType) {
            this.connection = connection;
            this.databaseType = databaseType;
        }

        @Override
        public SQLHelperInsertStatment insertInto(String tableName) throws Exception {
            if (tableName == null) {
                throw new NullPointerException("table name is null.");
            } else if (tableName.trim().isEmpty()) {
                throw new IllegalArgumentException("table name is empty.");
            }
            return new SQLHelperInsertStatmentImplementation(connection, tableName);
        }

        @Override
        public SQLHelperUpdateStatment update(String tableName) throws Exception {
            if (tableName == null) {
                throw new NullPointerException("table name is null.");
            } else if (tableName.trim().isEmpty()) {
                throw new IllegalArgumentException("table name is empty.");
            }
            return new SQLHelperUpdateStatmentImplementation(connection, tableName);
        }

        @Override
        public SQLHelperDeleteStatment deleteFrom(String tableName) throws Exception {
            if (tableName == null) {
                throw new NullPointerException("table name is null.");
            } else if (tableName.trim().isEmpty()) {
                throw new IllegalArgumentException("table name is empty.");
            }
            return new SQLHelperDeleteStatmentImplementation(connection, tableName);
        }

        @Override
        public SQLHelperSelectStatment selectFrom(String tableName) throws Exception {
            if (tableName == null) {
                throw new NullPointerException("table name is null.");
            } else if (tableName.trim().isEmpty()) {
                throw new IllegalArgumentException("table name is empty.");
            }
            return new SQLHelperSelectStatmentImplementation(connection, tableName, databaseType);
        }

    }

// -------------------------------------------- EXCEPTION -------------------------------------------------------- \\
    /**
     * An exception that provides information on SQLHelper error, not necessary
     * a database error.
     *
     * @author Saleh Haddawi
     */
    static class SQLHelperException extends SQLException {

        String message;

        public SQLHelperException(String message) {
            this.message = message;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }
}
