#!/usr/bin/env bash
# Fails when a git-tracked text file is not valid UTF-8, has a UTF-8 BOM, contains CRLF, or is
# double-encoded UTF-8 (mojibake: o-acute stored as bytes \xC3\x83\xC2\xB3 instead of \xC3\xB3, incl. cp1252 variants). Binary files and *.bat are skipped, as
# are files .gitattributes marks as -text/binary. Needs only git + perl (both in Git Bash and ubuntu-latest).
# Usage: bash scripts/check-text-encoding.sh
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

git -c core.quotePath=false ls-files --eol | perl -e '
use strict; use warnings; use Encode qw(decode FB_CROAK);
my $bad = 0;
sub fail { my ($f, $l, $m) = @_; print STDERR "$f:$l: $m\n"; $bad++; }
while (my $row = <STDIN>) {
  chomp $row;
  my ($meta, $path) = split /\t/, $row, 2;
  next if $meta =~ m{attr/-text} || $meta =~ m{i/-text} || $path =~ /\.bat$/i;
  open(my $fh, "<:raw", $path) or next;
  local $/; my $data = <$fh>; close $fh;
  next if !defined $data || $data eq "" || index($data, "\0") >= 0;
  fail($path, 1, "UTF-8 BOM") if $data =~ /\A\xEF\xBB\xBF/;
  my $line = 0;
  for my $l (split /\n/, $data, -1) {
    $line++;
    fail($path, $line, "CRLF line ending") if $l =~ /\r$/;
    my $copy = $l;
    if (!eval { decode("UTF-8", $copy, FB_CROAK); 1 }) { fail($path, $line, "invalid UTF-8"); next; }
    # A-tilde (\xC3\x83) or A-circumflex (\xC3\x82) re-encoded, followed by a Latin-1 or cp1252 character.
    fail($path, $line, "double-encoded UTF-8 (mojibake)") if $l =~ /\xC3\x83(?:\xC2[\x80-\xBF]|\xE2\x80[\x90-\xBF]|\xE2\x82\xAC|\xE2\x84\xA2|\xC5[\x92\x93\xA0\xA1\xB8\xBD\xBE]|\xC6\x92|\xCB[\x86\x9C])|\xC3\x82\xC2[\x80-\xBF]/;
  }
}
if ($bad) { print STDERR "check-text-encoding: $bad problem(s)\n"; exit 1; }
print "check-text-encoding: OK\n";
'
