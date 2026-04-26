# MySQL Setup (Campus Event Backend)

## 1. Create schema manually (optional)
Run in MySQL:

```sql
SOURCE C:/Users/krish/OneDrive/Desktop/JAVA_PROGRAME/src/database/01_schema.sql;
SOURCE C:/Users/krish/OneDrive/Desktop/JAVA_PROGRAME/src/database/02_seed.sql;
```

`db.DatabaseInitializer` also creates tables and roles at runtime automatically.

## 2. JDBC environment variables (PowerShell)
```powershell
$env:EVENT_DB_URL="jdbc:mysql://localhost:3306/campus_event_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:EVENT_DB_USER="root"
$env:EVENT_DB_PASSWORD="root"
```

## 3. Compile and run
Download `mysql-connector-j` jar and keep it in:

`C:\Users\krish\OneDrive\Desktop\JAVA_PROGRAME\src\lib\mysql-connector-j-9.3.0.jar`

Compile:
```powershell
cd C:\Users\krish\OneDrive\Desktop\JAVA_PROGRAME
javac -cp ".;src\lib\mysql-connector-j-9.3.0.jar" -d bin src\db\*.java src\model\*.java src\service\*.java src\main\main.java
```

Run:
```powershell
java -cp "bin;src\lib\mysql-connector-j-9.3.0.jar" main.main
```

Note:
- This backend is now fully database-driven (no ArrayList fallback mode).
- If DB credentials are wrong, app startup stops until DB connection works.

Default app logins:
- `k@gmail.com / 123` (student)
- `admin@gmail.com / admin` (admin)
- `superadmin@gmail.com / super123` (super_admin manager)
