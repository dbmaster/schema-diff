import com.branegy.dbmaster.model.*
import com.branegy.dbmaster.sync.api.*

def p_storage_folder = com.branegy.util.DataDirHelper.getDataDir()+"/schema-diff";
String result = new MassSchemaDiffHistory(logger, p_from_date, p_to_date, dbm)
    .getHistory(p_database_query,p_storage_folder);

print result.isEmpty()?"No changes were found":result;


