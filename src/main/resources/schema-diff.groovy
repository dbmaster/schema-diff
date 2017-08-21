import com.branegy.dbmaster.database.api.ModelService
import com.branegy.dbmaster.model.*

import io.dbmaster.sync.*

import com.branegy.dbmaster.sync.api.SyncService

import java.util.regex.*

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

modelService = dbm.getService(ModelService.class)

def source_server   = p_source_connection
def source_database = p_source_connection_db

def target_server   = p_target_connection
def target_database = p_target_connection_db


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
        ignoreObjects.split(",|\r\n|\n").each{ token ->
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
         "^(?<header>.*?)"
        +"create"
        +"\\s+"
        +"(?<type>view|function|procedure|trigger)"
        +"\\s+"
            // scheme
            +"(?:"                          
                +"(?<schema>"
                     +"(?:\\[[^\\]]+\\])" // [name]
                     +"|"                 // or
                     +"(?:\"[^\"]+\")"    // "name"
                     +"|"                 // or   
                     +"[^\\.\\s]+"        // any-string
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
            +"(?:"
                // trigger on
                +"(?:\\s*on\\s+" 
                    // scheme
                    +"(?:"
                        +"(?<tschema>"
                             +"(?:\\[[^\\]]+\\])" // [name]
                             +"|"                 // or
                             +"(?:\"[^\"]+\")"    // "name"
                             +"|"                 // or
                             +"[^\\.\\s]+"        // any-string
                        +")"
                        +"\\."
                    +")?"
                    // name
                    +"(?<tname>"
                         +"(?:\\[[^\\]]+\\])" // [name]
                         +"|"                 // or
                         +"(?:\"[^\"]+\")"    // "name"
                         +"|"                 // or
                         +"[^\\s]+"           // any-string
                    +")"
                    +"\\s+"
                +")"
                +"|"
                +"(?:(?<suffix>"              // (..(),()) optional
                    +"\\s*\\("                // 
                        + "(?:"
                            +"[^(]*"          // non (
                            +"\\("            // (
                            +"[^)]*"          // non )
                            +"\\)"            // ) 
                        + ")*"                  
                        + "[^)]*"             // other
                        +"\\)"                // )
                    +")?"
                +")" 
            +")"
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
            // -- asdas \n create function Func2()
            //create trigger dbo.Func on F2 ...
        ,Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL).matcher("");
    
    def strip = {String name ->
        name = StringUtils.strip(name,"\"");
        if (!name.startsWith("[")){
            name = '['+name+ ']';
        }
        return name;
    }
    
    String defaultSchema = StringUtils.isEmpty(p_default_schema)?"[dbo]":strip(StringUtils.stripEnd(p_default_schema,"."));
    StringBuilder builder = new StringBuilder(64*1024);
    def normalize = {DatabaseObject<?> object ->
        if (object.source == null) {
            logger.warn("The source is not found for "+object.name);
            return;
        }

        if (!matcher.reset(object.source).matches()) {
            return;
        } 
        
        String header = matcher.group("header");
        String type = matcher.group("type").toLowerCase();
        String schema = matcher.group("schema");
        String name = matcher.group("name");
        String triggerSchema = matcher.group("tschema");
        String triggerName = matcher.group("tname");
        String suffix = matcher.group("suffix");
        String other = matcher.group("other");
        
        builder.setLength(0);
        if (header!=null) {
            builder.append(header.trim());
            builder.append("\r\n");
        }
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
            } else if (type == "trigger") {
                builder.append(" on ");
                if (triggerSchema == null) {
                    builder.append(defaultSchema);
                } else {
                    builder.append(strip(triggerSchema));
                }
                builder.append('.');
                builder.append(strip(triggerName));
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
    
    for (table in model.tables) {
        for (trigger in table.triggers) {
            normalize(trigger);
        }
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

def normalizeExtraBrackets = { Model model ->
    if (model == null) {
        return;
    }
    
    final Matcher NORMALIZE_BRACKETS_MATCHER = Pattern.compile(
        "\\(\\s*"
            +"("
                +"[+-]?"
                +"(?:"
                    +"\\.\\d+"                    // .y
                    +"|"
                    +"\\d+\\."                    // x.  
                    +"|"
                    +"\\d+"                       // x
                    +"|"
                    +"\\d+\\.\\d+"                // x.y
                 +")"
            +")"
        +"\\s*\\)").matcher("");
    final StringBuffer sb = new StringBuffer(16*1024);
    
    def normalizeNumber = {String number -> 
        if (number.charAt(0) == '+') {
            number = number.substring(1);
        }
        boolean negative = false;
        try {
            if (number.charAt(0) == '-') {
                negative = true;
                number = number.substring(1);
            }
            long i;
            long f;
            int dot = number.indexOf('.');
            if (dot == number.length()-1) { // x.
                i = Long.parseLong(number.substring(0,dot));
                f = 0L;
            } else if (dot==0) {            // .y
                i = 0L;
                f = Long.parseLong(StringUtils.defaultIfEmpty(StringUtils.stripEnd(number.substring(1),"0"),"0"));
            } else if (dot>0) {             // x.y
                i = Long.parseLong(number.substring(0,dot));
                f = Long.parseLong(StringUtils.defaultIfEmpty(StringUtils.stripEnd(number.substring(dot+1),"0"),"0"));
            } else {                        // x
                i = Long.parseLong(number);
                f = 0L;
            }
            if (f!=0L) {
                return (negative?"-":"")+i+"."+f;
            } else {
                return ""+(negative?-i:i);
            }
        } catch (NumberFormatException e) {
            return (negative?"-":"")+number;
        }
    };
    
    def normalize = { String code ->
        if (code == null) {
            return code;
        }
        while (true) {
            NORMALIZE_BRACKETS_MATCHER.reset(code);
            if (!NORMALIZE_BRACKETS_MATCHER.find()) {
                return code;
            }
            sb.setLength(0);
            
            NORMALIZE_BRACKETS_MATCHER.appendReplacement(sb, normalizeNumber(NORMALIZE_BRACKETS_MATCHER.group(1)));
            while (NORMALIZE_BRACKETS_MATCHER.find()) {
                NORMALIZE_BRACKETS_MATCHER.appendReplacement(sb, normalizeNumber(NORMALIZE_BRACKETS_MATCHER.group(1)));
            }
            NORMALIZE_BRACKETS_MATCHER.appendTail(sb);
            code = sb.toString();
        }
    };
    
    String tmp; 
    for (table in model.tables) {
        for (column in table.columns) {
            if (column.defaultValue!=(tmp = normalize(column.defaultValue))) {
                column.defaultValue = tmp;
            }
        }
        for (constraint in table.constraints) {
            if (constraint.definition!=(tmp = normalize(constraint.definition))) {
                constraint.definition = tmp;
            }
        }
    }
}

def ignoreViewColumns = {Model model ->
    for (View view in model.views) {
        while (!view.columns.isEmpty()) {
            view.removeColumn(view.columns.get(view.columns.size()-1));
        }
    }
}


ignoreObjects(p_ignore_objects,sourceModel,targetModel);
if (p_preprocessing.contains("Normalize source code")) {
    normalizeSource(sourceModel);
    normalizeSource(targetModel);
}
if (p_preprocessing.contains("Normalize extra ()")) {
    normalizeExtraBrackets(sourceModel);
    normalizeExtraBrackets(targetModel);
}
if (p_preprocessing.contains("Ignore view columns")) {
    ignoreViewColumns(sourceModel);
    ignoreViewColumns(targetModel);
}
def ignoreWhitespaces = p_preprocessing.contains("Ignore whitespaces");
def ignoreColumnOrderChanges = p_preprocessing.contains("Ignore column order changes");
def ignoreRenamedCKs = p_preprocessing.contains("Ignore Renamed CKs");
def ignoreRenamedIndexes = p_preprocessing.contains("Ignore Renamed Indexes");

logger.info("Comparing databases")
def sync_session = modelService.compareObjects(sourceModel, targetModel, 
    [ignoreWhitespaces:ignoreWhitespaces,
     ignoreColumnOrderChanges:ignoreColumnOrderChanges,
     ignoreRenamedCKs:ignoreRenamedCKs,
     ignoreRenamedIndexes:ignoreRenamedIndexes
    ])

logger.info("Generating report")
def service = dbm.getService(SyncService.class)
def template = "/preview-model-generator.groovy"

def previewHtml = service.generateSyncSessionPreviewHtml(template, sync_session, p_show_changes_only)

println previewHtml

logger.info("Comparison completed successfully")