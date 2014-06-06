import com.branegy.dbmaster.database.api.ModelService
import com.branegy.dbmaster.model.*
import io.dbmaster.sync.*

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

def sync_session = new ModelSyncSession(dbm)

sync_session.sourceModel = sourceModel
sync_session.syncObjects(sourceModel, targetModel)
sync_session.setParameter("title", "Model Synchronization")
// TODO (implement) sync_session.setParameter("exclude_objects", p_exclude_objects==null ? "" : p_exclude_objects.join(","))
sync_session.setParameter("longText", "Procedure.Source;View.Source;Function.Source")

logger.info("Generating report")

println new PreviewGenerator(true).generatePreview(sync_session)

logger.info("Comparison completed sucessfully")
