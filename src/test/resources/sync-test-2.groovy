/*
 *  File Version:  $Id: sync-test.groovy 145 2013-05-22 18:10:44Z schristin $
 */

import groovy.io.FileType

import com.branegy.inventory.api.InventoryService
import com.branegy.dbmaster.database.api.ModelService
import com.branegy.dbmaster.model.*
import com.branegy.dbmaster.sync.api.*

invService = dbm.getService(InventoryService.class)
modelService = dbm.getService(ModelService)

def model = modelService.findModelByName(p_model_name, p_model_version, null)
def targetModel = modelService.fetchModel(model.getConnectionName(), model.getOptions())
def syncSession = modelService.compareModel(model, targetModel)
        
def preview = invService.generateDBSyncPreview(syncSession)
println "<div>" + preview + "</div>"
syncSession.applyChanges()
