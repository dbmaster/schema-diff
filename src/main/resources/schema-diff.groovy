import com.branegy.dbmaster.database.api.ModelService
import com.branegy.dbmaster.model.*

import io.dbmaster.sync.*

import com.branegy.dbmaster.sync.api.SyncService

import java.util.regex.*

import org.apache.commons.lang.StringUtils;

modelService = dbm.getService(ModelService.class)

def source_server =   p_source_database.split("\\.",2)[0]
def source_database = p_source_database.split("\\.",2)[1]

def target_server =   p_target_database.split("\\.",2)[0]
def target_database = p_target_database.split("\\.",2)[1]


RevEngineeringOptions source_options = new RevEngineeringOptions()
source_options.database = source_database
source_options.importViews = true
source_options.importProcedures = true

logger.info("Loading source database")

sourceModel = modelService.fetchModel(source_server, source_options)

RevEngineeringOptions target_options = new RevEngineeringOptions()
target_options.database = target_database
target_options.importViews = true
target_options.importProcedures = true

logger.info("Loading target database")
targetModel = modelService.fetchModel(target_server, target_options)

/*sourceModel  = new ModelConverter(logger).
  convertModel(sourceModel, targetModel.getCustomData("dialect"), 
                            targetModel.getCustomData("dialect_version"))
*/

def ignoreObjects(String ignoreObjects, Model source,Model target) {
    if (ignoreObjects != null) {
        Map<String,Set<Matcher>> ignore = [:] // type <-> regexp
        
        def getOrCreate = { map, key ->
            def result = map[key]
            if (result == null) {
                result = map[key] = [:];
            }
            return result;
        }
        
        def toMatcher = { String query ->  
            StringBuilder pattern = new StringBuilder(query.length()*2);
            def start = 0;
            for(int j = 0; j < query.length(); ++j){
               switch (query.charAt(j)) {
                   case '*':
                   pattern.append(Pattern.quote(query.substring(start,j)));
                   pattern.append(".*");
                   start = j+1;
                   break;
                   case '?':
                   pattern.append(Pattern.quote(query.substring(start,j)));
                   pattern.append('.');
                   start = j+1;
                   break;
               }
            }
            if (start == 0){
                pattern.append(Pattern.quote(query));
            }
            return Pattern.compile(pattern.toString(),Pattern.CASE_INSENSITIVE).matcher("");
        }
        
        Matcher tokenMatcher = Pattern.compile("(table|view|procedure|function)\\s*:\\s*(.+)",Pattern.CASE_INSENSITIVE).matcher("");
        ignoreObjects.split(",").each{ token ->
            token = token.trim();
            if (tokenMatcher.reset(token).matches()) { // by type
                def type  = tokenMatcher.group(1).toLowerCase();
                def value = tokenMatcher.group(2);
                getOrCreate(ignore,type).put(toMatcher(value),token);
            } else { // any type
                def matcher = toMatcher(token);
                getOrCreate(ignore,"table").put(matcher,token);
                getOrCreate(ignore,"view").put(matcher,token);
                getOrCreate(ignore,"procedure").put(matcher,token);
                getOrCreate(ignore,"function").put(matcher,token);
            }
        }
        
        def filter = { Map<Matcher,String> matchers, Model parent, String type, String extraInfo ->
           if (matchers != null) {
               def toRemove = [];
               def objects;
               switch (type) {
                   case "table": objects = parent.tables; break;
                   case "view": objects = parent.views; break;
                   case "procedure": objects = parent.procedures; break;
                   case "function": objects = parent.functions; break;
               }
               for (object in objects) {
                  def name = object.getName();
                  for (e in matchers.entrySet()) {
                      def matcher = e.key
                      matcher.reset(name);
                      if (matcher.matches()) {
                          toRemove.add(object);
                          logger.debug("Ingore {} {} {} by {}",extraInfo,type,name,e.value);
                      }
                  }
               }
               switch (type) {
                   case "table":
                       for (object in toRemove) {
                           parent.removeTable(object);
                       }
                       break;
                   case "view":
                       for (object in toRemove) {
                           parent.removeView(object);
                       }
                       break;
                   case "procedure":
                       for (object in toRemove) {
                           parent.removeProcedure(object);
                       }
                       break;
                   case "function":
                       for (object in toRemove) {
                           parent.removeFunction(object);
                       }
                       break;
               }
           }
        };
        
        filter(ignore["table"],source,"table","source");
        filter(ignore["table"],target,"table","target");
        
        filter(ignore["view"],source,"view","source");
        filter(ignore["view"],target,"view","target");
        
        filter(ignore["function"],source,"function","source");
        filter(ignore["function"],target,"function","target");
        
        filter(ignore["procedure"],source,"procedure","source");
        filter(ignore["procedure"],target,"procedure","target");
    }
}

def normalizeSource = {Model model ->
    Matcher matcher = Pattern.compile(
        "^\\s*"
        +"create"
        +"\\s+"
        +"(?<type>view|function|procedure)"
        +"\\s+"
            // scheme
            +"(?:"                          
                +"(?<schema>"
                     +"(?:\\[[^\\]]+\\])" // [name]
                     +"|"                 // or
                     +"(?:\"[^\"]+\")"    // "name"
                     +"|"                 // or   
                     +"[^\\.]+"           // any-string
                +")"
                +"\\."
            +")?"
            // name
            +"(?<name>"
                 +"(?:\\[[^\\]]+\\])" // [name]
                 +"|"                 // or
                 +"(?:\"[^\"]+\")"    // "name"
                 +"|"                 // or
                 +"[^\\s(]+"          // any-string
            +")"
            +"\\s*(?<suffix>\\(.*?\\))?" // (..) optional
            +"\\s*(?<other>.+?)\\s*"
        +"\$"
            //create view dbo.name
            //create view name
            //create view [dbo].name
            //create view [name]
            //create view "name"
            //create view otherschemaname.objectname
            //create function [dbo].[Func1]()
            //create function Func2()
            //Create function dbo.Func3()
            //Create function [dbo].Func4()
            //CREATE function dbo.[Func5]()
            //CREATE function dbo.[Function with spaces]()
            //create procedure dbo.Proc1
        ,Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL).matcher("");
    
    def strip = {String name ->
        name = StringUtils.strip(name,"[]\"");
        if (name.contains(" ")) {
            name = '['+name+ ']';
        }
        return name;
    }
    
    String defaultSchema = StringUtils.isEmpty(p_default_schema)?"dbo":strip(StringUtils.stripEnd(p_default_schema,"."));
    StringBuilder builder = new StringBuilder(64*1024);
    def normalize = {ModelObject object ->
        if (!matcher.reset(object.source).matches()) {
            return;
        } 
        
        String type = matcher.group("type");
        String schema = matcher.group("schema");
        String name = matcher.group("name");
        String suffix = matcher.group("suffix");
        String other = matcher.group("other");
        
        builder.setLength(0);
        builder.append("create ");
        builder.append(type);
        builder.append(' ');
        if (schema == null) {
            builder.append(defaultSchema);
        } else {
            builder.append(strip(schema));
        }
        builder.append('.');
        builder.append(strip(name));
        if (suffix == null) {
            if (type == "function" || type == "procedure") {
                builder.append("()");
            }
        } else {
            builder.append('(');
            builder.append(suffix.substring(1,suffix.length()-1).trim());
            builder.append(')');
        }
        builder.append(' ');
        builder.append(other);
        
        object.source = builder.toString();
    }
    
    
    for (view in model.views) {
        normalize(view);
    }
    for (function in model.functions) {
        normalize(function);
    }
    for (procedure in model.procedures) {
        normalize(procedure);
    }
}


ignoreObjects(p_ignore_objects,sourceModel,targetModel);
if (p_preprocessing.contains("Normalize source code")) {
    normalizeSource(sourceModel);
    normalizeSource(targetModel);
}

logger.info("Comparing databases")
def sync_session = modelService.compareModel(sourceModel, targetModel)

logger.info("Generating report")
def service = dbm.getService(SyncService.class)
def template = "/preview-model-generator.groovy"

def previewHtml = service.generateSyncSessionPreviewHtml(template, sync_session, p_show_changes_only)

println previewHtml

logger.info("Comparison completed successfully")