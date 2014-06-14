import com.branegy.dbmaster.database.api.ModelService
import com.branegy.dbmaster.model.*
import io.dbmaster.sync.*
import com.branegy.dbmaster.sync.api.SyncService;

modelService = dbm.getService(ModelService.class)

def source_server =   p_source_database.split("\\.")[0]
def source_database = p_source_database.split("\\.")[1]

def target_server =   p_target_database.split("\\.")[0]
def target_database = p_target_database.split("\\.")[1]


RevEngineeringOptions source_options = new RevEngineeringOptions()
source_options.database = source_database
source_options.importViews = true
source_options.importProcedures = true


logger.info("Loading source database")

sourceModel = modelService.fetchModel(source_server, source_options)

RevEngineeringOptions target_options = new RevEngineeringOptions()
target_options.importViews = true
target_options.importProcedures = true

target_options.database = target_database

logger.info("Loading target database")
targetModel = modelService.fetchModel(target_server, target_options)

logger.info("Comparing databases")
def sync_session = modelService.compareModel(sourceModel, targetModel)

logger.info("Generating report")
println dbm.getService(SyncService.class).generateSyncSessionPreviewHtml(sync_session, true)

logger.info("Comparison completed sucessfully")
