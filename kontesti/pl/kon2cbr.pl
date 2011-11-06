#!/usr/bin/perl

# kon2cbr.pl kontesti.pl:n lokiformaattia syövä ja cabrilloa ulostava skripti

# $Id: kon2cbr.pl,v 0.4 2003/01/09 19:46:44 goblet Exp $
#-----------------------------------------------------------------------------
use strict;

my ($formats) = {
  joulu     => "%4d %2s %10s %4s %-11s %-3s %-3s %-5s %-11s %-3s %-3s %-5s",
  perus     => "%4d %2s %10s %4s %-11s %-3s %-3s %-5s %-11s %-3s %-3s %-5s",
  sainio    => "%4d %2s %10s %4s %-11s %-3s %-3s %-5s %-11s %-3s %-3s %-5s",
  nrau      => "%4d %2s %10s %4s %-14s %-3s %-3s %-2s %-14s %-3s %-3s %-2s",
  kalakukko => "%4d %2s %10s %4s %-14s %-3s %-3s %-2s %-14s %-3s %-3s %-2s",
  '6cup'    => "%4d %2s %10s %4s %-14s %-3s %-3s %-2s %-14s %-3s %-3s %-2s",
  syys      => "%4d %2s %10s %4s %-14s %-3s %-3s %-2s %-14s %-3s %-3s %-2s"
};

#-----------------------------------------------------------------------------
# komentoriviparametrit hashiin

sub getopt {
  my $i = 0;
  my ($ret) = {};
  while ($i < $#ARGV+1) {
    if ($ARGV[$i] =~ /^-/) {
      if ($ARGV[$i+1] =~ /^-/ || $i == $#ARGV) {
        $ret->{substr($ARGV[$i],1)} = 1;
      } else {
        $ret->{substr($ARGV[$i++],1)} = $ARGV[$i+1];
      }
    }
    $i++;
  }
  return $ret;
}

#-----------------------------------------------------------------------------
# ja se main()

sub main {
  my $logfile = $ARGV[$#ARGV];
  my ($opts) = getopt();
  my $contest = lc($opts->{c});
  my $mycall = uc($opts->{m});

  if (!$logfile or !$mycall or !$formats->{$contest}) {
    print "Käyttö: $0 [-d VVVV-KK-PP] -m omakutsu -c kilpailunimi lokitiedosto\n";
    print "\tkilpailunimet: perus, sainio, syys, joulu, kalakukko, nrau, 6cup\n";
    return 1;
  }
  my $date;
  if ($opts->{d}) {
    $date = $opts->{d};
  } else {
    my @t = gmtime();
    $date = sprintf("%4d-%02d-%02d",$t[5]+1900,$t[4]+1,$t[3]);
    print "date = $date\n";
  }
  if ($date !~ /^\d{4}\-\d{2}\-\d{2}$/) {
    print "Päiväyksen formaatti on VVVV-KK-PP\n";
    return 1;
  }

  open FDI, "<$logfile" or do {
    print "Ei voi avata tiedostoa $logfile\n";
    return 42;
  };
  open FDO, ">$logfile.cbr";

  while (my $row = <FDI>) {
    chomp $row;
    my @qso = split(/[\s\t]/,$row);
    (my $freq = $qso[9]) =~ s/,/./;
    $freq = $freq * 1000;
    (my $mode = $qso[10]) =~ s/SSB/PH/;
    my $out = sprintf($formats->{$contest},$freq,$mode,$date,$qso[1],$mycall,
                         $qso[3],$qso[4],$qso[5],$qso[2],$qso[6],$qso[7],$qso[8]);
    $out =~ s/null/    /;
    $out =~ s/nul/   /;
    print FDO "QSO: $out\r\n";
  }

  close FDI;
  close FDO;
  print "Tallennettu tiedostoon $logfile.cbr\n\n";
}
#-----------------------------------------------------------------------------
# hoblaa!
exit main();

