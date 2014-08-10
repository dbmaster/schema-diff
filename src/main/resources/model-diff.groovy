import com.branegy.dbmaster.database.api.ModelService
import com.branegy.dbmaster.model.*
import io.dbmaster.sync.*
import com.branegy.dbmaster.sync.api.SyncService
import com.branegy.dbmaster.model.Index.IndexType

modelService = dbm.getService(ModelService.class)

logger.info("Loading source model")

sourceModel = modelService.findModelById(p_source_model,Model.FETCH_TREE)
com.branegy.util.InjectorUtil.getInstance(javax.persistence.EntityManager.class).detach(sourceModel)

logger.info("Loading target model")
targetModel = modelService.findModelById(p_target_model)

logger.info("Converting source model")

new ModelConverter(logger).convertModel(sourceModel, 
                                        targetModel.getCustomData("dialect"), 
                                        targetModel.getCustomData("dialect_version"))

logger.info("Comparing")
def sync_session = modelService.compareModel(sourceModel, targetModel)

logger.info("Generating report")
def service = dbm.getService(SyncService.class)
def template = "/preview-model-generator.groovy"
def previewHtml = service.generateSyncSessionPreviewHtml(template, sync_session, true)

logger.debug("Generation completed")

println previewHtml

logger.info("Comparison completed successfully")