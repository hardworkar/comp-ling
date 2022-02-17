#!/usr/bin/env python3

import arxiv

for i in range(1):
  paper = next(arxiv.Search(id_list=["2107.14577"]).results())
  paper.download_pdf(filename=f"paper-{i}.pdf")
