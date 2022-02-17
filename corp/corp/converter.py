#!/usr/bin/env python3
import sys
import codecs

assert len(sys.argv) > 1

import json
with open(sys.argv[1], 'r') as f:
  with open('out.txt', 'w') as out:
    data = json.load(f)
    for line in data:
      out.write(line['text'] + '\n')
