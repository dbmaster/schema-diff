import groovy.io.FileType

import com.branegy.inventory.api.InventoryService
import com.branegy.dbmaster.database.api.ModelService
import com.branegy.dbmaster.model.*
import com.branegy.dbmaster.sync.api.*

def model

modelService = dbm.getService(ModelService)
    
model = modelService.findModelByName(p_model_name, p_model_version, null)
modelService.deleteModel(model.id)
            
logger.info("Deleting model ${model.id} ${p_model_name}")