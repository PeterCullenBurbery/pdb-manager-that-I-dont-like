/**
 * @since 2024-W41-3 18.08.00.724 -0400
 * @author peter
 */
package com.peter_burbery.PDB_manager.PDB_manager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

public class PDBManagerInteractive {

	// Method to establish a connection to the Oracle Database
	private static Connection getConnection(String serviceName, boolean isSysDBA) throws SQLException {
		String url = "jdbc:oracle:thin:@localhost:1521/" + serviceName; // Change as per your DB
		String user = isSysDBA ? "sys as sysdba" : "system";
		String password = "1234"; // SYSDBA password here
		return DriverManager.getConnection(url, user, password);
	}

	// Method to check if the PDB exists
	private static boolean checkIfPDBExists(Connection conn, String pdbName) throws SQLException {
		String query = String.format("SELECT name FROM v$pdbs WHERE name = '%s'", pdbName.toUpperCase());
		try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
			return rs.next(); // If there's a result, the PDB exists
		}
	}

	// Method to check for active connections in the PDB
	private static boolean checkForActiveConnections(Connection conn, String pdbName) throws SQLException {
		String query = String.format(
				"SELECT COUNT(*) FROM v$session WHERE con_id = (SELECT con_id FROM v$pdbs WHERE name = '%s') AND type != 'BACKGROUND'",
				pdbName.toUpperCase());
		try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
			if (rs.next()) {
				int activeConnections = rs.getInt(1);
				return activeConnections > 0;
			}
		}
		return false;
	}

	// Method to drop the PDB
	private static void dropPDB(Connection conn, String pdbName) throws SQLException {
		try (Statement stmt = conn.createStatement()) {
			String closePDBSQL = String.format("ALTER PLUGGABLE DATABASE %s CLOSE IMMEDIATE", pdbName);
			stmt.execute(closePDBSQL);

			String dropPDBSQL = String.format("DROP PLUGGABLE DATABASE %s INCLUDING DATAFILES", pdbName);
			stmt.execute(dropPDBSQL);

			System.out.println("PDB dropped successfully.");
		}
	}

	// Method to create a PDB
	private static void createPDB(Connection conn, String pdbName, String adminName, String adminPassword,
			String fileLocation) throws SQLException {
		try (Statement stmt = conn.createStatement()) {

			// Switch to CDB root
			stmt.execute("ALTER SESSION SET CONTAINER = CDB$ROOT");

			// Create Pluggable Database
			String createPDBSQL = String.format(
					"CREATE PLUGGABLE DATABASE %s " + "ADMIN USER %s IDENTIFIED BY %s "
							+ "FILE_NAME_CONVERT = ('C:\\APP\\PETER\\ORADATA\\ORCL\\PDBSEED', '%s')",
					pdbName, adminName, adminPassword, fileLocation);
			stmt.execute(createPDBSQL);

			// Open the PDB
			String openPDBSQL = String.format("ALTER PLUGGABLE DATABASE %s OPEN", pdbName);
			stmt.execute(openPDBSQL);

			// Save the state of the PDB
			String saveStateSQL = String.format("ALTER PLUGGABLE DATABASE %s SAVE STATE", pdbName);
			stmt.execute(saveStateSQL);

			System.out.println("Pluggable Database created successfully!");
		}
	}

	// Method to grant roles and privileges to the new user
	private static void grantRolesAndPrivileges(Connection conn, String pdbName, String username) throws SQLException {
		try (Statement stmt = conn.createStatement()) {

			// Switch to the new PDB
			String switchToPDBSQL = String.format("ALTER SESSION SET CONTAINER = %s", pdbName);
			stmt.execute(switchToPDBSQL);

			// Check if the user already exists
			String checkUserSQL = String.format("SELECT COUNT(*) FROM DBA_USERS WHERE USERNAME = '%s'",
					username.toUpperCase());
			ResultSet rs = stmt.executeQuery(checkUserSQL);
			if (rs.next() && rs.getInt(1) == 0) {
				throw new SQLException("User does not exist: " + username, "42000", 1918);
			}

			String[] roles = { "DBA", "PPLB_ROLE", "DV_MONITOR", "CTXAPP", "DV_AUDIT_CLEANUP", "EM_EXPRESS_ALL",
					"WM_ADMIN_ROLE", "OLAP_USER", "OLAP_XS_ADMIN", "DV_SECANALYST", "MAINTPLAN_APP",
					"RECOVERY_CATALOG_OWNER_VPD", "XS_CACHE_ADMIN", "AVTUNE_PKG_ROLE", "GDS_CATALOG_SELECT",
					"SCHEDULER_ADMIN", "PROVISIONER", "AUDIT_ADMIN", "XDB_WEBSERVICES_OVER_HTTP",
					"AQ_ADMINISTRATOR_ROLE", "SYSUMF_ROLE", "APPLICATION_TRACE_VIEWER", "XDB_WEBSERVICES", "LBAC_DBA",
					"OPTIMIZER_PROCESSING_RATE", "RECOVERY_CATALOG_USER", "DV_DATAPUMP_NETWORK_LINK", "GSMUSER_ROLE",
					"GATHER_SYSTEM_STATISTICS", "LOGSTDBY_ADMINISTRATOR", "DBJAVASCRIPT", "GSM_POOLADMIN_ROLE",
					"DV_ADMIN", "DV_POLICY_OWNER", "HS_ADMIN_ROLE", "XS_SESSION_ADMIN", "DV_GOLDENGATE_ADMIN",
					"IMP_FULL_DATABASE", "DV_XSTREAM_ADMIN", "DV_PATCH_ADMIN", "GGSYS_ROLE",
					"DATAPUMP_EXP_FULL_DATABASE", "EJBCLIENT", "HS_ADMIN_EXECUTE_ROLE", "JMXSERVER", "OLAP_DBA",
					"ADM_PARALLEL_EXECUTE_TASK", "JAVAIDPRIV", "SELECT_CATALOG_ROLE", "JAVADEBUGPRIV", "CONNECT",
					"ACCHK_READ", "DATAPUMP_IMP_FULL_DATABASE", "SODA_APP", "BDSQL_ADMIN", "OEM_MONITOR",
					"GSMADMIN_ROLE", "AQ_USER_ROLE", "JAVAUSERPRIV", "XDB_SET_INVOKER", "RECOVERY_CATALOG_OWNER",
					"JAVA_ADMIN", "DBFS_ROLE", "PDB_DBA", "RDFCTX_ADMIN", "DV_GOLDENGATE_REDO_ACCESS", "CDB_DBA",
					"JAVASYSPRIV", "GSMROOTUSER_ROLE", "HS_ADMIN_SELECT_ROLE", "AUDIT_VIEWER", "RESOURCE", "DV_OWNER",
					"XDB_WEBSERVICES_WITH_PUBLIC", "EXECUTE_CATALOG_ROLE", "DATAPATCH_ROLE", "DV_ACCTMGR",
					"EXP_FULL_DATABASE", "DBMS_MDX_INTERNAL", "DV_STREAMS_ADMIN", "XS_NAMESPACE_ADMIN", "BDSQL_USER",
					"ORDADMIN", "AUTHENTICATEDUSER", "CAPTURE_ADMIN", "OEM_ADVISOR", "XS_CONNECT", "XDBADMIN",
					"EM_EXPRESS_BASIC" };

			for (String role : roles) {
				String grantRoleSQL = String.format("GRANT \"%s\" TO %s", role, username);
				stmt.execute(grantRoleSQL);
			}

			String[] systemPrivileges = { "CREATE JOB", "DROP ANY CONTEXT", "UPDATE ANY CUBE",
					"ALTER ANY ANALYTIC VIEW", "DROP ANY TRIGGER", "DROP ANY SQL TRANSLATION PROFILE",
					"MANAGE ANY FILE GROUP", "ALTER PUBLIC DATABASE LINK", "MANAGE FILE GROUP", "ALTER ANY INDEX",
					"DROP ANY SEQUENCE", "ALTER PROFILE", "INHERIT ANY PRIVILEGES", "UNDER ANY TABLE", "KEEP SYSGUID",
					"CREATE ASSEMBLY", "DROP ANY LIBRARY", "ALTER ANY EDITION", "CREATE ROLE", "CREATE LIBRARY",
					"DROP ROLLBACK SEGMENT", "CREATE TRIGGER", "ALTER ANY PROCEDURE", "ADMINISTER DATABASE TRIGGER",
					"DROP ANY MEASURE FOLDER", "CREATE ANY PROCEDURE", "ALTER ANY OUTLINE", "CREATE ANY ANALYTIC VIEW",
					"EXECUTE ANY INDEXTYPE", "USE ANY JOB RESOURCE", "CREATE ANY DIRECTORY", "ALTER ANY RULE SET",
					"USE ANY SQL TRANSLATION PROFILE", "ALTER ANY MINING MODEL", "DEBUG CONNECT SESSION", "LOGMINING",
					"DROP ANY ATTRIBUTE DIMENSION", "CREATE ANY MINING MODEL", "CREATE LOCKDOWN PROFILE",
					"ALTER SESSION", "CREATE MATERIALIZED VIEW", "CREATE PLUGGABLE DATABASE", "DROP ANY ANALYTIC VIEW",
					"WRITE ANY ANALYTIC VIEW CACHE", "MERGE ANY VIEW", "CREATE ANY INDEX",
					"READ ANY ANALYTIC VIEW CACHE", "CREATE DIMENSION", "EXECUTE ANY RULE SET",
					"CREATE SQL TRANSLATION PROFILE", "ALTER ANY MATERIALIZED VIEW", "AUDIT SYSTEM", "CREATE OPERATOR",
					"MANAGE ANY QUEUE", "ALTER ANY SQL PROFILE", "GRANT ANY OBJECT PRIVILEGE", "CREATE INDEXTYPE",
					"AUDIT ANY", "INHERIT ANY REMOTE PRIVILEGES", "DEBUG ANY PROCEDURE", "CREATE ANY MEASURE FOLDER",
					"CREATE ANY SEQUENCE", "CREATE MEASURE FOLDER", "UPDATE ANY CUBE BUILD PROCESS", "CREATE VIEW",
					"ALTER DATABASE LINK", "ALTER ANY ASSEMBLY", "ALTER ANY SQL TRANSLATION PROFILE",
					"CREATE ANY EVALUATION CONTEXT", "SELECT ANY MINING MODEL", "DELETE ANY CUBE DIMENSION",
					"ALTER ANY TABLE", "ALTER ANY ATTRIBUTE DIMENSION", "CREATE SESSION", "CREATE RULE", "BECOME USER",
					"SELECT ANY CUBE BUILD PROCESS", "SELECT ANY TABLE", "INSERT ANY MEASURE FOLDER",
					"CREATE ANY SQL PROFILE", "FORCE ANY TRANSACTION", "DELETE ANY TABLE", "ALTER ANY SEQUENCE",
					"SELECT ANY CUBE DIMENSION", "CREATE ANY EDITION", "CREATE EXTERNAL JOB", "EM EXPRESS CONNECT",
					"DROP ANY MATERIALIZED VIEW", "CREATE ANY CUBE BUILD PROCESS", "FLASHBACK ANY TABLE",
					"DROP ANY RULE SET", "BACKUP ANY TABLE", "ALTER ANY CUBE", "CREATE CREDENTIAL", "CREATE TABLE",
					"EXECUTE ANY LIBRARY", "DROP ANY OUTLINE", "EXECUTE ASSEMBLY", "CREATE ANY HIERARCHY",
					"CREATE ANALYTIC VIEW", "CREATE ANY DIMENSION", "DROP ANY TABLE", "ADMINISTER KEY MANAGEMENT",
					"ALTER ANY CLUSTER", "EXECUTE ANY CLASS", "ALTER ANY CUBE BUILD PROCESS", "CREATE ANY CREDENTIAL",
					"DROP ANY DIMENSION", "SYSBACKUP", "CREATE ANY RULE SET", "SELECT ANY SEQUENCE", "UNDER ANY TYPE",
					"MANAGE TABLESPACE", "DROP ANY OPERATOR", "CREATE ANY OPERATOR", "DROP ANY HIERARCHY",
					"EXEMPT IDENTITY POLICY", "CREATE TYPE", "CREATE TABLESPACE", "SELECT ANY TRANSACTION",
					"DELETE ANY MEASURE FOLDER", "CREATE ANY CUBE", "LOCK ANY TABLE", "CREATE EVALUATION CONTEXT",
					"DROP ANY TYPE", "ADVISOR", "CREATE PUBLIC DATABASE LINK", "ANALYZE ANY",
					"CREATE ATTRIBUTE DIMENSION", "DROP ANY RULE", "INSERT ANY CUBE DIMENSION",
					"CREATE ROLLBACK SEGMENT", "CREATE ANY JOB", "ALTER USER", "QUERY REWRITE", "SELECT ANY DICTIONARY",
					"CREATE PUBLIC SYNONYM", "DROP LOGICAL PARTITION TRACKING", "GLOBAL QUERY REWRITE",
					"ALTER ANY CUBE DIMENSION", "CREATE ANY CUBE DIMENSION", "DROP ANY CLUSTER", "CREATE ANY RULE",
					"UPDATE ANY CUBE DIMENSION", "CREATE LOGICAL PARTITION TRACKING", "ADMINISTER RESOURCE MANAGER",
					"CREATE ANY SYNONYM", "DROP ANY SYNONYM", "DROP ANY MINING MODEL", "EXECUTE ANY PROCEDURE",
					"CREATE SYNONYM", "SET CONTAINER", "EXECUTE ANY PROGRAM", "EXEMPT REDACTION POLICY",
					"EXECUTE ANY TYPE", "ON COMMIT REFRESH", "DEBUG CONNECT ANY", "CREATE SEQUENCE", "CREATE HIERARCHY",
					"SELECT ANY MEASURE FOLDER", "COMMENT ANY MINING MODEL", "ADMINISTER SQL TUNING SET",
					"CREATE ANY INDEXTYPE", "KEEP DATE TIME", "DROP ANY INDEX", "RESTRICTED SESSION",
					"DEQUEUE ANY QUEUE", "ENABLE DIAGNOSTICS", "ANALYZE ANY DICTIONARY", "ALTER ANY INDEXTYPE",
					"TRANSLATE ANY SQL", "ADMINISTER ANY SQL TUNING SET", "CREATE USER", "EXECUTE ANY OPERATOR",
					"CREATE CUBE BUILD PROCESS", "CREATE PROFILE", "ALTER ANY ROLE", "UPDATE ANY TABLE",
					"ALTER ANY LIBRARY", "DROP ANY VIEW", "CREATE ANY CLUSTER", "EXECUTE ANY RULE", "ALTER TABLESPACE",
					"UNDER ANY VIEW", "EXECUTE ANY ASSEMBLY", "GRANT ANY PRIVILEGE", "ALTER ANY TRIGGER",
					"CREATE ANY VIEW", "ALTER LOCKDOWN PROFILE", "EXPORT FULL DATABASE", "ALTER ANY MEASURE FOLDER",
					"ALTER ANY EVALUATION CONTEXT", "TEXT DATASTORE ACCESS", "FLASHBACK ARCHIVE ADMINISTER",
					"IMPORT FULL DATABASE", "CREATE ANY OUTLINE", "COMMENT ANY TABLE", "READ ANY TABLE",
					"CREATE DATABASE LINK", "DROP PUBLIC SYNONYM", "DROP USER", "CHANGE NOTIFICATION",
					"CREATE MINING MODEL", "INSERT ANY TABLE", "DROP LOCKDOWN PROFILE", "DROP PROFILE",
					"CREATE ANY MATERIALIZED VIEW", "CREATE RULE SET", "EXEMPT ACCESS POLICY", "MANAGE SCHEDULER",
					"READ ANY FILE GROUP", "FORCE TRANSACTION", "DROP ANY CUBE BUILD PROCESS", "ALTER ANY TYPE",
					"DROP ANY PROCEDURE", "CREATE ANY SQL TRANSLATION PROFILE", "DROP PUBLIC DATABASE LINK",
					"DROP ANY INDEXTYPE", "DROP ANY SQL PROFILE", "EXECUTE DYNAMIC MLE", "ALTER SYSTEM",
					"UNLIMITED TABLESPACE", "DROP ANY ROLE", "ALTER ANY DIMENSION", "DROP ANY CUBE DIMENSION",
					"DROP ANY CUBE", "CREATE ANY TRIGGER", "DROP ANY ASSEMBLY", "CREATE ANY TABLE",
					"ADMINISTER SQL MANAGEMENT OBJECT", "DROP ANY DIRECTORY", "ENQUEUE ANY QUEUE",
					"DROP ANY EVALUATION CONTEXT", "CREATE ANY ASSEMBLY", "CREATE ANY TYPE", "REDEFINE ANY TABLE",
					"CREATE CLUSTER", "CREATE ANY CONTEXT", "EXECUTE ANY EVALUATION CONTEXT", "RESUMABLE",
					"CREATE ANY LIBRARY", "DROP ANY EDITION", "CREATE PROCEDURE", "ALTER DATABASE", "SELECT ANY CUBE",
					"GRANT ANY ROLE", "ALTER ANY RULE", "CREATE ANY ATTRIBUTE DIMENSION", "CREATE CUBE DIMENSION",
					"ALTER ANY OPERATOR", "CREATE CUBE", "ALTER RESOURCE COST", "ALTER ANY HIERARCHY",
					"DROP TABLESPACE", "ALTER ROLLBACK SEGMENT", "PURGE DBA_RECYCLEBIN" };

			for (String privilege : systemPrivileges) {
				String grantPrivilegeSQL = String.format("GRANT %s TO %s", privilege, username);
				stmt.execute(grantPrivilegeSQL);
			}

			System.out.println("Roles and privileges granted to user " + username + ".");
		}
	}

	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);

		try (Connection conn = getConnection("orcl.localdomain", true)) {

			String pdbName;
			while (true) {
				// Gather input from the user
				System.out.println("Enter the PDB name:");
				pdbName = scanner.nextLine();
				int length = pdbName.length();

				System.out.println("PDB name length: " + length);
				if (length > 30) {
					String truncatedPdbName = pdbName.substring(0, 30);
					System.out.println("PDB name truncated to 30 characters: " + truncatedPdbName);
					System.out.println("Characters that must be removed: " + (length - 30));
					System.out.println("Characters removed: " + pdbName.substring(30));

					System.out.println("Do you want to use the truncated name? (yes/no)");
					String response = scanner.nextLine();

					if (response.equalsIgnoreCase("yes")) {
						pdbName = truncatedPdbName;
						break;
					} else {
						System.out.println("Please enter a new PDB name.");
					}
				} else {
					break;
				}
			}

			// Check if the PDB exists right after entering the PDB name
			if (checkIfPDBExists(conn, pdbName)) {
				System.out.println("PDB " + pdbName + " already exists.");

				// Check for active connections
				if (checkForActiveConnections(conn, pdbName)) {
					System.out.println("There are active connections to the PDB. Cannot drop the PDB.");
					return;
				} else {
					// Offer to close and drop the PDB
					System.out.println(
							"No active connections found. Do you want to drop the PDB and recreate it? (yes/no)");
					String response = scanner.nextLine();

					if (response.equalsIgnoreCase("yes")) {
						dropPDB(conn, pdbName);
					} else {
						System.out.println("Exiting without creating the PDB.");
						return;
					}
				}
			}

			// Replace underscores with hyphens in the PDB name
			String generatedFolderName = pdbName.replace("_", "-");
			String defaultStorageLocation = "C:\\oracle-pluggable-database\\" + generatedFolderName;

			// Suggest the generated folder name even if a truncated PDB name was used
			System.out.println("Would you like to use the generated folder name for storage location: "
					+ defaultStorageLocation + "? (yes/no)");
			String useDefaultLocation = scanner.nextLine();

			String fileLocation;
			if (useDefaultLocation.equalsIgnoreCase("yes")) {
				fileLocation = defaultStorageLocation;
			} else {
				// Ask if the user wants to generate a suggested name
				System.out.println("Would you like to generate a storage location based on your PDB name? (yes/no)");
				String generateSuggestion = scanner.nextLine();

				if (generateSuggestion.equalsIgnoreCase("yes")) {
					System.out.println("Enter the file storage location base name:");
					String enteredLocation = scanner.nextLine();
					String suggestedFileLocation = "C:\\oracle-pluggable-database\\"
							+ enteredLocation.replace("_", "-");

					System.out.println("Would you like to use the modified file storage location: "
							+ suggestedFileLocation + "? (yes/no)");
					String useSuggestedLocation = scanner.nextLine();

					if (useSuggestedLocation.equalsIgnoreCase("yes")) {
						fileLocation = suggestedFileLocation;
					} else {
						System.out.println("Enter the file storage location:");
						fileLocation = scanner.nextLine();
					}
				} else {
					// Allow user to enter a file storage location with a suggestion
					System.out.println("Enter the file storage location base name:");
					String enteredLocation = scanner.nextLine();
					String suggestedFileLocation = "C:\\oracle-pluggable-database\\"
							+ enteredLocation.replace("_", "-");

					System.out.println("Would you like to use the modified file storage location: "
							+ suggestedFileLocation + "? (yes/no)");
					String useSuggestedLocation = scanner.nextLine();

					if (useSuggestedLocation.equalsIgnoreCase("yes")) {
						fileLocation = suggestedFileLocation;
					} else {
						System.out.println("Enter the file storage location:");
						fileLocation = scanner.nextLine();
					}
				}
			}

			// Continue gathering remaining input
			System.out.println("Enter the admin username:");
			String adminName = scanner.nextLine();

			System.out.println("Enter the admin password:");
			String adminPassword = scanner.nextLine();

			// Create the PDB
			createPDB(conn, pdbName, adminName, adminPassword, fileLocation);

			// Grant roles and privileges to the admin user
			grantRolesAndPrivileges(conn, pdbName, adminName);

		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			scanner.close();
		}
	}
}