import com.branegy.dbmaster.model.*
import com.branegy.dbmaster.sync.api.*

String result = new MassSchemaDiffHistory(logger, p_from_date, p_to_date, dbm)
    .getHistory(p_database_query,p_storage_folder);

print result.isEmpty()?"No changes was found":result;


