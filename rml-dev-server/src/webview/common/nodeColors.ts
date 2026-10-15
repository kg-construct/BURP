import {TagVariant} from "baukasten-ui/core";

export function getNodeTagColor(type: string): TagVariant {
    switch ((type || '').toLowerCase()) {
        // Core structural container
        case 'triplesmap':
            return 'info';

        // Logical Sources & Views
        case 'csvsource':
        case 'jsonsourcerfc':
        case 'rdbsource':
        case 'sparqlfilesource':
        case 'sparqlservicesource':
        case 'xmlsource':
        case 'logicalsource':
        case 'abstractlogicalsource':
        case 'logicalview':
            return 'success';

        // Term Maps & Generators
        case 'subjectmap':
        case 'predicateobjectmap':
        case 'predicatemap':
        case 'objectmap':
        case 'referencingobjectmap':
        case 'graphmap':
        case 'datatypemap':
        case 'languagemap':
        case 'termmap':
        case 'expressionmap':
            return 'secondary';

        // Joins & Conditions
        case 'joincondition':
        case 'condition':
        case 'viewjoin':
            return 'error';

        // Collections & Containers
        case 'gathermap':
            return 'info';

        // Functions & Parameters
        case 'functionexecution':
        case 'functionmap':
        case 'parametermap':
        case 'returnmap':
        case 'input':
        case 'inputvaluemap':
            return 'default';

        // Expressions
        case 'template':
        case 'rawreference':
        case 'csvreference':
        case 'jsonpathreference':
        case 'rdbreference':
        case 'sparqlreference':
        case 'sparqltsvreference':
        case 'xmlreference':
        case 'logicalreference':
        case 'reference':
        case 'childmap':
        case 'parentmap':
        case 'expressionfield':
        case 'iterablefield':
        case 'field':
            return 'primary';
        case 'rdfnodeconstant':
            return 'primary';

        default:
            return 'default';
    }
}
