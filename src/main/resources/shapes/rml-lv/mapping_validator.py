from logging import debug, info, critical
from pyshacl import validate
from rdflib import Graph
from shutil import get_terminal_size


class MappingValidator:
    def __init__(self, shape: str, ontology: str) -> None:
        sg = Graph()
        sg.parse(shape, format='turtle')
        self._shape = sg
        og = Graph()
        og.parse(ontology, format='turtle')
        self._ont = og

    def validate(self, rules: Graph, print_report: bool = True) -> None:
        valid: bool
        report_graph: Graph
        report_text: str
        valid, report_graph, report_text = validate(rules,
                                                    shacl_graph=self._shape,
                                                    ont_graph=self._ont,
                                                    do_owl_imports=True)
        debug(f'RML rules valid: {valid}')
        debug(f'SHACL validation report: {report_text}')

        # If mapping rules are invalid, print SHACL report and raise exception
        if not valid and print_report:
            self._print_report(report_text)
            msg = 'RML mapping rules are invalid, a detailed explanation' \
                ' is available in the report'
            critical(msg)
            raise ValueError(msg)

    def _print_report(self, report_text: str) -> None:
        tty_columns: int
        tty_columns, _ = get_terminal_size()
        info('-' * tty_columns)
        title: str = 'RML rules validation report'
        white_space: int = int((tty_columns - len(title)) / 2)
        title = ' ' * white_space + title + ' ' * white_space
        info(title)
        info('-' * tty_columns)
        info(report_text)
        info('-' * tty_columns)
