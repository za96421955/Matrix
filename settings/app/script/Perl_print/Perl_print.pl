#!/usr/bin/perl
use JSON;
print decode_json($ARGV[0])->{print} . "\n";