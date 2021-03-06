import com.branegy.dbmaster.model.*
import com.branegy.dbmaster.model.Index.IndexType
import org.slf4j.Logger

public class ModelConverter {
    final Logger logger;

    public ModelConverter(Logger logger) {
        this.logger = logger;
    }

    public Model convertModel(Model model, String toDialect, String toDialectVersion) {
        String dialect = model.getCustomData("dialect")
        logger.info("Converting ${dialect} to ${toDialect} v${toDialectVersion}")
        if (dialect==null || !dialect.equals("MySQL") || toDialect==null || !toDialect.equals("sqlserver")) {
            return model
        }

        model.tables.each { table ->
            table.setName("dbo."+table.name)
            table.columns.each { column ->
                    String identity = column.getCustomData("Extra")
                    column.setCustomData("is_identity",(identity!=null && identity.equals("auto_increment")) ? 1 : null)

                    switch (column.type.toLowerCase()) {
                        // INTEGER TYPES
                    case "bit": 
                    // TODO Review bit type 
                    // http://www.xaprb.com/blog/2006/04/11/bit-values-in-mysql/
                    // Guide to Migrating from MySQL to SQL Server 2012 (pdf)
                    // http://blogs.technet.com/b/bpaulblog/archive/2010/06/13/mysql-to-ms-sql-server-2008-r2-migration-experience-with-ssma.aspx
                    // http://convertdb.com/features
                    case "point":
                        column.setType("TODO")
                        break;
                    case "tinyint":  // tinyint is unsigned
                        column.setType("tinyint")
                        column.setSize(null)
                        break;
                    case "smallint":
                        column.setType("smallint")
                        column.setSize(null)
                        break;
                    case "mediumint":
                    case "int":
                        column.setType("int")
                        column.setSize(null)
                        break;
                    case "bigint":
                        column.setType("bigint")
                        column.setSize(null)
                        break;
                    case "decimal":
                        column.setType("decimal")
                        break;
                    // FLOATING POINT (TODO require revision)
                    case "real":
                    case "float":
                        column.setType("float")
                        column.setSize(24)
                        // column.setPrecision(null)
                        column.setPrecesion(null)
                        break;
                    case "double":
                        column.setType("float")
                        column.setSize(53)
                        column.setPrecesion(null)
                        // column.setPrecision(null)
                        break;
                    // DATE AND TIME
                    case "datetime":
                        column.setType("datetime2")
                        column.setSize(null)
                        break;
                    case "date":
                        column.setType("date")
                        break;
                    case "time":
                        column.setType("time")
                        break;
                    case "timestamp":
                        column.setType("smalldatetime")
                        break;
                    case "year":
                        column.setType("smallint")
                        break;
                    // STRING    
                    case "char":
                        column.setType("char")
                        // statement+=" CHAR(${column.size}) ";
                        break;
                    case "varchar":
                        column.setType("varchar")
                        // statement+=" VARCHAR(${column.size})";
                        break;
                    case "tinytext":
                    case "text":
                    case "mediumtext":
                    case "longtext":
                        column.setType("text")
                        break;
                    default: 
                        throw new RuntimeException("Unexpected data type ${column.type}")
                }
            }
                
            table.indexes.each { index ->            
                switch (index.type) {
                    case IndexType.BTree:
                        index.setType(index.primaryKey ? IndexType.Clustered : IndexType.Nonclustered)
                        break;
                    default: 
                        throw new RuntimeException("Unexpected index type ${index.type}")
                }
            }
        }
        return model
    }

}