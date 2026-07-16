args <- commandArgs(trailingOnly = TRUE)
cat(gsub('.*"print"\\s*:\\s*"([^"]*)".*', '\\1', args[1]), "\n")