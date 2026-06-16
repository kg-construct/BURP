#!/usr/bin/env python
#
# (c) Dylan Van Assche (2020 - 2023)
# IDLab - Ghent University - imec
# MIT license
#

import os
import argparse
import unittest
import logging
from parameterized import parameterized
from glob import glob
from rdflib import Graph

from mapping_validator import MappingValidator
from utils import replace_strings_in_file, cleanup_tmp_folder

TEST_CASES_DIR = os.path.join(os.path.abspath('../test-cases'), '*/*.ttl')
SHAPE_FILE_LOCATION = 'io.ttl'
ONTOLOGY_FILE_LOCATION = '../ontology/documentation/ontology.ttl'


class MappingValidatorTests(unittest.TestCase):
    def _validate_rules(self, path: str) -> None:
        rules = Graph().parse(path, format='turtle')

        # Replace import statements
        shapes = replace_strings_in_file(SHAPE_FILE_LOCATION, {
            '<http://w3id.org/rml/core/shapes>': '<https://raw.githubusercontent.com/kg-construct/rml-core/refs/heads/main/shapes/core.ttl>'
        })
        ontology = replace_strings_in_file(ONTOLOGY_FILE_LOCATION, {
            '<http://w3id.org/rml/core/>': '<https://raw.githubusercontent.com/kg-construct/rml-core/refs/heads/main/ontology/documentation/ontology.ttl>'
        })

        mapping_validator = MappingValidator(shapes, ontology)
        mapping_validator.validate(rules)

        cleanup_tmp_folder(shapes)
        cleanup_tmp_folder(ontology)

    def test_non_existing_mapping_rules(self) -> None:
        with self.assertRaises(FileNotFoundError):
            p = os.path.abspath('does_not_exist.ttl')
            self._validate_rules(p)

    @parameterized.expand([(p,) for p in sorted(glob(TEST_CASES_DIR))],
                          skip_on_empty=True)
    def test_validation_rules(self, path: str) -> None:
        """
        Test if our SHACL shapes are able to validate our validation mapping
        rules test cases.
        """
        print(f'Testing validation with: {path}')
        self._validate_rules(path)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Execute tests for SHACL '
                                     'shapes on RML mapping rules.')
    parser.add_argument('--verbose', '-v', action='count', default=1,
                        help='Set verbosity level of messages. Example: -vvv')
    args = parser.parse_args()

    args.verbose = 70 - (10 * args.verbose) if args.verbose > 0 else 0
    logging.basicConfig(level=args.verbose,
                        format='%(asctime)s %(levelname)s: %(message)s',
                        datefmt='%Y-%m-%d %H:%M:%S')

    print(TEST_CASES_DIR)
    unittest.main(failfast=False)

