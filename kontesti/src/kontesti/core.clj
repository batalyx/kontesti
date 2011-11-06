(ns kontesti.core)

;; #!/usr/bin/perl

;; # $Id: kontesti.pl,v 0.40 2007/11/04 12:40:27 goblet Exp $
;; # $Revision: 0.40 $

;; ##########################################################################
;; # kontesti.pl                                                            #
;; #                                                                        #
;; # OH2MMY                                                                 #
;; #                                                                        #
;; # Toiminee useimmissa kotimaankisoissa. Joudut vain laskemaan pisteet    #
;; # toistaiseksi itse.                                                     #
;; #                                                                        #
;; # Jos lˆyd‰t vikoja, niin korjaa, tukea ei ole toistaiseksi saatavilla,  #
;; # koska t‰m‰ on tehty vain itselle ja vain siksi kun kotimaankisoihin    #
;; # ohjelmia saa vain microsoft-kakalle.                                   #
;; #                                                                        #
;; ##########################################################################

;; use strict;
;; use Socket;
;; use Term::ReadKey;
;; use Term::Cap;
;; use Term::ANSIColor;
;; $Term::ANSIColor::AUTORESET = 1;

;; my $logfile = $ARGV[$#ARGV];
;; my ($opts) = getopt();
;; my $contest = lc($opts->{c});
;; if ($contest eq "joulu") {$contest = "sainio";}

;; if (!$logfile or $contest !~ /(perus-[pksy]|sainio|syys|nrau|kalakukko|joulu|6cup)/) {
;;   print "K‰yttˆ: $0 -c kilpailunimi lokitiedosto\n";
;;   print "\tkilpailunimet: perus-p, perus-k, perus-s, perus-y, sainio, syys,\n";
;;   print "\t               joulu, kalakukko, nrau, 6cup\n"; 
;;   print "\tPeruskisoissa luokkakirjain p=perus, k=kerho, s=second-op y=yleis\n";
;;   exit(1);
;; }

;; my ($pos) = {
;;   clock => [ 8, -12 ],
;;   call => [ 14, -12 ],
;;   msg => [ 62, -12 ],
;;   outmsg => [ 38, -12 ],
;;   band => [ 1, -12 ],
;;   mode => [ 3, -12 ],
;;   rst_s => [ 32, -12 ],
;;   rst_g => [ 56, -12 ],
;;   cwlog => [ 0, 1 ],
;;   last_qsos => [ 0, 6 ],
;;   status => [ 1, -2 ],
;;   multi => [ 1, -10 ],
;;   workingmode => [ 65, -2 ],
;;   score => [ 55, 1 ],
;;   keyer => [ 9, -2 ],
;;   helps => [ 1, -6 ],
;;   lastout => [ 1, 1 ]
;; };

;; my ($item) = {
;;   call => '',
;;   msg => '',
;;   outmsg => '    ',
;;   band => '80',
;;   mode => 'CW ',
;;   rst_s => '59',
;;   rst_g => '59',
;;   status => '',
;;   workingmode => 'CQ',
;;   keyer => '',
;;   lastout => ''
;; };

;; my ($wordlen) = {
;;   'perus-p' => 5,
;;   'perus-k' => 5,
;;   'perus-s' => 5,
;;   'perus-y' => 5,
;;   sainio  => 5,
;;   syys    => 2,
;;   kalakukko => 2,
;;   '6cup'    => 2,
;;   nrau    => 2
;; };

;; my ($dupe) = {};

;; my ($bandswap) = {
;;   '3,5' => 80,
;;   7 => 40,
;;   14 => 20,
;;   21 => 15,
;;   28 => 10,
;;   10 => 28,
;;   15 => 21,
;;   20 => 14,
;;   40 => 7,
;;   80 => '3,5'
;; };

;; # klunssia

;; my ($termkeys) = {
;;   _kP => "\e[5~",
;;   _kN => "\e[6~",
;;   _kh => "\e[H",
;;   '_@7' => "\e[F",
;;   _ku => "\e[A",
;;   _up => "\e[A",
;;   _kd => "\e[B",
;;   _do => "\e[B",
;;   _kl => "\e[D",
;;   _le => "\e[D",
;;   _kr => "\e[C",
;;   _kD => "\e[3~",
;;   _kI => "\e[2~",
;;   _nd => "\e[C"
;; };

;; my ($multi) = {};
;; if ($contest eq "6cup") {$multi->{'40-OH6'} = 0;$multi->{'80-OH6'} = 0;}

;; my ($fieldlen) = {
;;   call => 15,
;;   msg => 9,
;;   outmsg => 9,
;;   keyer => 50
;; };
;; if ($contest =~ /(kalakukko|syys|6cup|nrau)/) {
;;   $fieldlen->{msg} = 6;
;;   $fieldlen->{outmsg} = 6;
;; }


;; my ($conf) = {};
;; my ($countyname) = {};
;; my $stopped = 0;
;; my $stdout = *STDOUT;
;; my $where = "call";
;; my @last_qsos = ();
;; my @cwmsgs = ('','','','');
;; my $qso_num = 0;
;; my $clock_stopped = 0;
;; my $_right_ = ' ';
;; my $position;
;; my $multipliers = 0;
;; my $points = 0;
;; my $editor_row = "";
;; my $backup_item;
;; my $editmode = 0;
;; my $editqso = 0;
;; my $editqso_utc;
;; #-----------------------------------------------------------------------------
;; # komentoriviparametrit hashiin

;; sub getopt {
;;   my $i = 0;
;;   my ($ret) = {};
;;   while ($i < $#ARGV+1) {
;;     if ($ARGV[$i] =~ /^-/) {
;;       if ($ARGV[$i+1] =~ /^-/ || $i == $#ARGV) {
;;         $ret->{substr($ARGV[$i],1)} = 1;
;;       } else {
;;         $ret->{substr($ARGV[$i++],1)} = $ARGV[$i+1];
;;       }
;;     }
;;     $i++;
;;   }
;;   return $ret;
;; }

;; #-----------------------------------------------------------------------------
;; # konffifileen luku

;; sub read_config {
;;   open CONF, "<kontesti.conf" or die "konffifile hukassa";
;;   while (my $row = <CONF>) {
;;     chomp $row;
;;     $row =~ s/^#.*//g;
;;     my ($key,$val) = split("=",$row);
;;     if ($key =~ /meta-\d/) {$val =~ tr/a-zÂ‰ˆ/A-Z≈ƒ÷/;}
;;     if ($key) {
;;       $conf->{$key} = $val;
;;     }
;;   }
;;   close CONF;
;;   $conf->{metakey} =~ s/\\e/\e/;
;;   foreach my $i (split(",", $conf->{cwdaemon_init})) {
;;     $i =~ s/\\e/\e/g;
;;     cw_out($i);
;;   }
;; }

;; #-----------------------------------------------------------------------------
;; # kerroin/kuntalistan luku 
;; sub read_dom {
;;   if ($contest =~ /(sainio|6cup)/) {
;;     open DOM, "<kunnat.dom";
;;     while (my $row = <DOM>) {
;;       chomp $row;
;;       my ($foo,$bar) = split(" ",$row,2);
;;       $countyname->{$foo} = $bar;
;;     }
;;     close DOM;
;;   }
;; }
;; #-----------------------------------------------------------------------------
;; # helppi-ikkuna

;; sub help_win() {
;;   my $key;
;;   my $width = 58;

;;   $main::term->Tgoto('cm',4,4,$stdout);
;;   print color('black on_cyan');
;;   $main::term->Tputs('as',1,$stdout);
;;   print "l" . "q" x $width . "k\r\n";
;;   for(0..9) {
;;     print $_right_ x 4 ."x";
;;     $main::term->Tputs('ae',1,$stdout);
;;     print " " x $width;
;;     $main::term->Tputs('as',1,$stdout);
;;     print "x\r\n";
;;   }
;;   print $_right_ x 4 . "m" . "q" x $width . "j";
;;   $main::term->Tputs('ae',1,$stdout);
;;   $main::term->Tgoto('cm',0,5,$stdout);

;;   print $_right_ x 6 . "Meta-n‰pp‰in on ";
;;   if ($conf->{metakey} eq "\e") {print "ESC"} else {print $conf->{metakey};}
;;   print "\r\n\r\n";
;;   my @helplist = split("\n",q{
;;      <b>Meta-B</b>   bandi               <b>PGUP</b>     RST++
;;      <b>Meta-M</b>   mode CW/SSB         <b>PGDN</b>     RST--
;;      <b>Meta-N</b>   nro/ohc toisto      <b>-</b>        viim. QSO:n poisto
;;      <b>Meta-S</b>   sanan toisto        <b>+</b>        workmode
;;      <b>Meta-R</b>   viestin toisto      <b>CTRL-C</b>   lopetus
;;      <b>Meta-K</b>   keyer-mode          <b>CTRL-L</b>   ruudun p‰ivitys
;;      <b>Meta-ESC</b> stop CW             <b>CTRL-R</b>   konfiguraatio
;;      <b>Meta-nro</b> CW-makroviesti      <b>CTRL-U</b>   kent‰n tyhjennys
;;   });

;;   foreach my $i (@helplist) {
;;     if ($i) {
;;       $i =~ s/^\s+/$_right_ x 6/e;
;;       $i =~ s/<b>/color('bold white')/eg;
;;       $i =~ s/<\/b>/color('reset black on_cyan')/eg;
;;       print "$i\r\n";
;;     }
;;   }

;;   print color('reset');
;;   go("status");

;;   while ($key eq "") {
;;     $key = Term::ReadKey::ReadKey(-1);
;;     select(undef,undef,undef,0.01);
;;   }
;; }

;; #-----------------------------------------------------------------------------
;; # piirret‰‰n grafiikkakehykset 

;; sub draw_frames {
;;   my $middleline = "t" . "q" x 78 . "u";
;;   my ($width, $height, $pixwidth, $pixheight) = Term::ReadKey::GetTerminalSize;
;;   $main::term->Tgoto('cm',0,0,$stdout);
;;   $main::term->Tputs('as',1,$stdout);
;;   for(0..($height-1)) {
;;     print "x";
;;     $main::term->Tputs('ae',1,$stdout);
;;     print $_right_ x 78;
;;     $main::term->Tputs('as',1,$stdout);
;;     print "x\r\n";
;;   }
;;   $main::term->Tgoto('cm',0,0,$stdout);
;;   $main::term->Tputs('as',1,$stdout);
;;   print "l" . "q" x 78 . "k\r\n";
;;   $main::term->Tputs('ae',1,$stdout);

;;   foreach my $i ("call","last_qsos","status","multi","helps") {
;;     my ($col,$row) = (@{$pos->{"$i"}});
;;     if ($row < 0) {
;;       $row = $height + $row;
;;     }
;;     $row--;
;;     $main::term->Tgoto('cm',0,$row,$stdout);
;;     $main::term->Tputs('as',1,$stdout);
;;     print "$middleline\r\n";
;;     $main::term->Tputs('ae',1,$stdout);
;;   }

;;   $main::term->Tgoto('cm',0,($height-1),$stdout);
;;   $main::term->Tputs('as',1,$stdout);
;;   print "m" . "q" x 78 . "j";
;;   $main::term->Tputs('ae',1,$stdout);
;; }

;; #-----------------------------------------------------------------------------
;; # siirr‰ kursori nimettyyn paikkaan

;; sub go {
;;   my ($col,$row) = (@{$pos->{"@_"}});
;;   my ($width, $height, $pixwidth, $pixheight) = Term::ReadKey::GetTerminalSize;
;;   if ($row < 0) {
;;     $row = $height + $row;
;;   }
;;   $main::term->Tgoto('cm',$col,$row,$stdout);
;; }

;; #-----------------------------------------------------------------------------
;; # siirr‰ kursoria oikealle riitt‰v‰sti $position:n mukaan
  
;; sub go_pos {
;;   print $_right_ x $position;
;; }

;; #-----------------------------------------------------------------------------
;; # editoitava rivi $item:iin

;; sub qso_to_item {
;;   my @foo = split("\t",$last_qsos[$editor_row]);
;;   $editqso = $foo[0];
;;   $editqso_utc = $foo[1];
;;   $item->{call} = $foo[2];
;;   $item->{rst_s} = substr($foo[3],0,2);
;;   $item->{outmsg} = "$foo[4] $foo[5]";
;;   $item->{rst_g} = substr($foo[6],0,2);
;;   $item->{msg} = "$foo[7] $foo[8]";
;;   $item->{band} = $bandswap->{$foo[9]};
;;   $item->{mode} = substr("$foo[10] ",0,3);
;;   $position = length($item->{call});
;;   go("call");
;;   go_pos();
;; }

;; #-----------------------------------------------------------------------------
;; # kellon p‰ivitt‰j‰
;; # jos parametriksi annetaan 1, niin p‰ivitt‰‰ vaikkei olisi tasaminuutti.

;; sub clock {
;;   (my $force) = @_;
;;   my @tim = gmtime();
;;   # dupelista ja kertoimet tyhj‰ksi tasatunnilla sainiossa ja syysottelussa
;;   # sek‰ kalakukossa ja kuutoscupissa
;;   if ($tim[0] == 0) {
;;     if ($tim[1] == 0 and $contest =~ /(kalakukko|sainio|syys|6cup)/) {
;;       ($dupe) = {};
;;       ($multi) = {};
;;       upd_scr("multi");
;;     }
;;     # peruskisassa tasavartein dupelista tyhj‰ksi ja sanasta huomautus
;;     if ($contest =~ /perus/ and $tim[1] % 15 == 0) {
;;       ($dupe) = {};
;;       $item->{status} = color('bold red')."VAIHDA SANA".color('reset');
;;       upd_scr("status");
;;     }
;;   }
;;   if ($editmode) {
;;     go("clock");
;;     print "    ";
;;     go($where);go_pos();
;;   }
;;   if ($clock_stopped) {return;}
;;   if ($force or $tim[0] == 0) {
;;     $main::term->Tputs('sc',1,$stdout);
;;     go("clock");
;;     printf("%02d%02d",$tim[2],$tim[1]);
;;     $main::term->Tputs('rc',1,$stdout);
;;   }
;; }

;; #-----------------------------------------------------------------------------
;; # p‰ivit‰ nimetty paikka ruudulla

;; sub upd_scr {
;;   (my $what) = @_;
;;   if ($what eq "lastout") {return;}
;;   $main::term->Tputs('sc',1,$stdout);
;;   go($what);
;;   if ($what eq "call" or $what eq "msg") {
;;     print color 'white on_blue';
;;     print $item->{$what} . " " x (16-length($item->{$what}));
;;     print color 'reset';
;;   } elsif ($what =~ /^rst_/) {
;;     if ($what eq "rst_s") {
;;       print color 'black on_cyan';
;;     } else {
;;       print color 'black on_white'; 
;;     }
;;     print " " . $item->{$what};
;;     if ($item->{mode} eq "CW ") {
;;       print "9 ";
;;     } else {
;;       print "  ";
;;     }
;;     print color 'reset';
;;   } elsif ($what eq "outmsg") {
;;     print color 'black on_cyan';
;;     print $item->{$what} . " " x (16-length($item->{$what}));
;;     print color 'reset';
;;   } elsif ($what eq "last_qsos") {
;;     foreach my $i (@last_qsos) {
;;       if ($last_qsos[$editor_row] eq $i and $last_qsos[$editor_row] ne "") {
;;         print color('blue on_white');
;;       } 
;;       print $_right_;
;;       my @qso = split(" ", $i);
;;       if ($qso[0] =~ /^DUPE/) {print color('white on_red');}
;;       $qso[0] =~ s/^DUPE\d{4}/DUPE/;
;;       my $band = $qso[9];
;;       $qso[9] = $bandswap->{$band};
;;       printf("%-7s%-6s%-17s%5s%5s %-15s%-5s%3s %-5s %s%s\r\n",@qso);
;;       print color('reset');
;;     }
;;   } elsif ($what eq "cwlog") {
;;     foreach my $i (@cwmsgs) {
;;       $i =~ s/\e/\\e/;
;;       printf("$_right_%-50s\r\n",$i);
;;     }
;;   } elsif ($what eq "status") {
;;     printf("%-60s", $item->{$what});
;;   } elsif ($what eq "multi") {
;;     my @mlist;
;;     foreach my $key (sort keys %$multi) {
;;       if (substr($key,0,3) eq $item->{band}."/") {
;;         push @mlist, substr($key,3);
;;       }
;;     }
;;     if ($contest =~ /(sainio|6cup)/) {
;;       printf("%-78s", join(",",@mlist));
;;     }
;;     if ($contest =~ /(kalakukko|nrau|syys)/) {
;;       (my $foo = join(",",@mlist[0..25])) =~ s/,*$//;
;;       printf("%-78s\r\n", $foo);
;;       ($foo = join(",",@mlist[26..51])) =~ s/,*$//;
;;       printf("$_right_%-78s\r\n", $foo);
;;       ($foo = join(",",@mlist[52..77])) =~ s/,*$//;
;;       printf("$_right_%-78s", $foo);
;;     }
;;     if ($contest eq "6cup") {
;;       my $key = $item->{band}."-OH6";
;;       print "\r\n$_right_\OH6:".$multi->{$key};
;;     }
;;   } elsif ($what eq "score") {
;;     print color('bold white on_black');
;;     if ($contest =~ /(nrau|6cup)/) {
;;       printf("%23s",sprintf("%d \xD7 %d = %d",
;;          $multipliers,$points,$multipliers * $points)); 
;;     }
;;     if ($contest =~ /perus/) {
;;       printf("%23s",sprintf("%d",$points));
;;     }
;;     if ($contest =~ /(kalakukko|syys|sainio)/) {
;;       printf("%23s",sprintf("%d \xD7 40 + %d = %d",
;;          $multipliers, $points, $multipliers * 40 + $points));
;;     }


;;     print color('reset');
;;   } elsif ($what eq "workingmode") {
;;     printf("workmode: ".color('bold')."%-3s".color('reset'),$item->{$what});
;;   } elsif ($what eq "keyer") {
;;     printf("%-50s",$item->{$what});
;;   } elsif ($what eq "mode" or $what eq "band") { 
;;     print color 'bold white on_black';
;;     print $item->{$what};
;;     print color 'reset';
;;   } elsif ($what eq "helps") {
;;     print_helps();
;;   } else {
;;     print $item->{$what};
;;   }
;;   print color ('reset');
;;   $main::term->Tputs('rc',1,$stdout);
;; }

;; #-----------------------------------------------------------------------------
;; # cw:t‰ ilmoille ja ruutu ajan tasalle

;; sub cw_out {
;;   my $message = "@_";

;;   $message =~ s/%/$conf->{mycall}/g;
;;   $message =~ s/@/$item->{call}/g;

;;   socket (SOCKET, PF_INET, SOCK_DGRAM, getprotobyname("udp"));
;;   my $ipaddr = inet_aton($conf->{cwdaemon_host});
;;   my $portaddr = sockaddr_in($conf->{cwdaemon_port}, $ipaddr);
;;   send(SOCKET, "$message \0", 0, $portaddr);
;;   close SOCKET;

;;   if ($message !~ /(^\e|^)$/) {
;;     push @cwmsgs, $message;
;;     shift @cwmsgs;
;;     upd_scr("cwlog");
;;   }
;; }

;; #-----------------------------------------------------------------------------
;; # CW:ss‰ numeroiden lyhennys konfiguraation mukaan
        
;; sub cw_shorten {
;;   my $message = "@_";
;;   if ($conf->{shortnums} =~ /0/) {$message =~ s/0/T/g;}
;;   if ($conf->{shortnums} =~ /1/) {$message =~ s/1/A/g;}
;;   if ($conf->{shortnums} =~ /9/) {$message =~ s/9/N/g;}
;;   return $message;
;; }


;; #-----------------------------------------------------------------------------
;; # varoita duplikaatista

;; sub alert_dupe {
;;   $item->{status} = color 'white on_red';
;;   $item->{status} .= "DUPE!";
;;   $item->{status} .= color 'reset';
;;   upd_scr("status");
;; }
;; #-----------------------------------------------------------------------------
;; # pisteiden lasku ja kertoimien tarkistus ja mahdollinen lis‰ys listaan
   
;; sub points_and_multi {
;;   my ($call,$band,$msg) = @_;
;;   my $key = "";
;;   my $district = substr($conf->{ohc},0,1);
;;   if ($contest =~ /perus-y/) {
;;     if ($msg =~ /^[01234567][0-9][0-9] [A-Z]{5}$/) {
;;         $points += 10;
;;     } else { 
;; 	$points += 2;
;;     }
;;   } elsif ($contest =~ /perus-[pks]/) {
;;     $points += 2;
;;   } elsif ($contest eq "sainio") {
;;      if ($msg =~ /^[0-9]{3} [A-Z≈ƒ÷]{5}$/) {
;;        $points += 10;
;;        $key = "$band/OH".substr($msg,0,1);
;;      } else {
;;        $points += 5;
;;      }
;;   } elsif ($contest =~ /(nrau|syys)/) {
;;      if ($msg =~ /^[0-9]{3} [A-Z≈ƒ÷]{2}$/) {
;;        $points += 2;
;;        $key = "$band/".substr($msg,4);
;;      } else {
;;        $points += 1;
;;      }
;;   } elsif ($contest =~ /kalakukko/) {
;;      if ($msg =~ /^[0-9]{3} [A-Z≈ƒ÷]{2}$/) {
;;        $points += 10;
;;        $key = "$band/".substr($msg,4);
;;      } else {
;;        $points += 5;
;;      }
;;   } elsif ($contest eq "6cup") {
;;      if (substr($msg,0,1) != $district) {
;;        $key = "$band/OH".substr($msg,0,1);
;;      }
;;      if ($district != 6 and substr($msg,0,1) == 6) {
;;        $multi->{"$band-OH6"} += 1;
;;        $multipliers++;
;;      }
;;      if ($msg =~ /^[0-9]{3} [A-Z≈ƒ÷]{2}$/) {
;;        $points += 2;
;;      } else {
;;        $points += 1;
;;      }
;;      if ($msg =~ /^[0-9]{3} [A-Z≈ƒ÷]{2}$/ and substr($msg,0,1) == 6) {
;;        $points += 2;
;;      } else {
;;        $points += 1;
;;      }
;;   }
;;   if ($key ne "") {
;;     if ($multi->{$key} < 1) {$multipliers++}
;;     $multi->{$key} = 1;
;;   }
;;   upd_scr("multi");
;; }     

;; #-----------------------------------------------------------------------------
;; # kysy varmistus lopettamiselle

;; sub ask_quit {
;;   my $key;
;;   my $was = $where;
;;   go("status");
;;   $where = "keyer";
;;   print color 'white on_red';
;;   print "Haluatko oikeasti lopettaa? (K/E)";
;;   print color 'reset';
;;   print " ";
;;   while ($key !~ m/^[ekny]$/) {
;;     $key = Term::ReadKey::ReadKey(-1);
;;     select(undef,undef,undef,0.01);
;;   }
;;   if ($key eq "e" or $key eq "n") {
;;     $item->{status} = "hienoa!";
;;     upd_scr("status");
;;     $where = $was;
;;     go($where);
;;     go_pos();
;;     return 0;
;;   }
;;   if ($key eq "k" or $key eq "y") {
;;     return 1;
;;   }
;; }

;; #-----------------------------------------------------------------------------
;; # kysy varmistus viimeisen qso:n poistolle

;; sub ask_del_last {
;;   my $key;
;;   go("status");
;;   $where = "keyer";
;;   print color 'white on_red';
;;   print "poistetaanko viimeisin QSO? (K/E)";
;;   print color 'reset';
;;   print " ";
;;   while ($key !~ m/^[ekny]$/) {
;;     $key = Term::ReadKey::ReadKey(-1);
;;     select(undef,undef,undef,0.01);
;;   }
;;   if ($key eq "e" or $key eq "n") {
;;     $item->{status} = "OK";
;;     upd_scr("status");
;;     return 0;
;;   } 
;;   if ($key eq "k" or $key eq "y") {
;;     (my $id = pop @last_qsos) =~ s/(^DUPE|\s.*)//g;
;;     delete_qso($id);
;;     $item->{status} = "$id POISTETTU";
;;     upd_scr("status");
;;     read_last_qsos();
;;     upd_scr("last_qsos");
;;     return 1;
;;   }
;; }

;; #-----------------------------------------------------------------------------
;; # statusrivin tyhjennys
;; sub clear_status {
;;   $item->{status} = "";
;;   upd_scr("status");
;; }

;; #-----------------------------------------------------------------------------
;; # makronappuloiden helpit

;; sub print_helps {
;;   go("helps");
;;   my $count = 0;
;;   my $metakey = $conf->{metakey};
;;   if ($metakey eq "\e") {$metakey = "esc";}
;;   foreach my $i (grep(/^meta-/, keys %$conf)) {
;;     print color 'bold';
;;     print $metakey . substr("$i ",5,2);
;;     print color 'reset';
;;     my $txt = $conf->{$i};
;;     $txt =~ s/%/$conf->{mycall}/g; 
;;     printf("%-20s ",substr($txt,0,20));
;;     if (++$count > 2) {
;;       print "\r\n" . $_right_;
;;       $count = 0;
;;     }
;;   }
;; }

;; #-----------------------------------------------------------------------------
;; # qso:n tallennus tiedostoon

;; sub save_qso {
;;   my ($logrow) = @_;
;;   open FD, ">>$logfile";
;;   $logrow =~ s/\s/\t/g;
;;   my @qso = split("\t",$logrow);
;;   my $band = $bandswap->{$qso[9]};
;;   my $dupekey = "";
;;   if ($contest =~ /(kalakukko|sainio|syys|6cup)/) {
;;     $dupekey = $qso[2]."-".$band;
;;   } elsif ($contest =~ /(perus|nrau)/) {
;;     $dupekey = $qso[2];
;;   }
;;   $dupe->{$dupekey} = 1;
;;   print FD "$logrow\n";
;;   close FD;
;;   $clock_stopped = 0;
;;   points_and_multi($qso[2],$band,"$qso[7] $qso[8]");
;;   upd_scr("score");
;;   push @last_qsos, $logrow;
;;   shift @last_qsos;
;; }

;; #-----------------------------------------------------------------------------
;; # qso:n poisto id:ll‰

;; sub delete_qso {
;;   my ($id) = @_;
;;   $id =~ s/^DUPE//;
;;   open FD1, "<$logfile";
;;   open FD2, ">>$logfile.tmp";
;;   while (my $row = <FD1>) {
;;     $row =~ s/^DUPE//;
;;     if ($row !~ m/^$id/) {
;;       print FD2 "$row";
;;     }
;;   }
;;   close FD1;
;;   close FD2;
;;   rename "$logfile.tmp", $logfile;
;;   $clock_stopped = 0;
;; }

;; #-----------------------------------------------------------------------------
;; # qso:n tietojen p‰ivitys id:ll‰
    
;; sub update_qso {
;;   my ($id,$logrow) = @_;
;;   $logrow =~ s/\s/\t/g;
;;   open FD1, "<$logfile";
;;   open FD2, ">>$logfile.tmp";
;;   while (my $row = <FD1>) {
;;     if ($row !~ m/^$id/) {  
;;       print FD2 "$row";
;;     } else {
;;       print FD2 "$logrow\n";
;;     }
;;   }
;;   close FD1;
;;   close FD2;
;;   rename "$logfile.tmp", $logfile;
;; }

;; #-----------------------------------------------------------------------------
;; # piirret‰‰n kaikki ruudun jutut uusiksi jne.

;; sub refresh_screen {
;;   $main::term->Tputs('cl',1,$stdout);
;;   read_last_qsos();
;;   draw_frames();      
;;   foreach my $i (keys %$item) {upd_scr($i);}
;;   upd_scr("last_qsos");
;;   upd_scr("multi");
;;   upd_scr("score");
;;   print_helps();
;;   clock(1);
;; }


;; #-----------------------------------------------------------------------------
;; # lasketaan viimeisten qsojen listaan mahtuva m‰‰r‰

;; sub lastqso_listlen {
;;   my ($width, $height, $pixwidth, $pixheight) = Term::ReadKey::GetTerminalSize;
;;   my ($col, $row, $tempmax, $maxrow);
;;   ($col, $row) = (@{$pos->{last_qsos}});
;;   $maxrow = $height;
;;   if ($row < 0) {$row = $height + $row;}

;;   foreach my $i (keys %$pos) {
;;     ($col, $tempmax) = (@{$pos->{"$i"}});
;;     if ($tempmax < 0) {$tempmax = $height + $tempmax;}
;;     if ($maxrow > $tempmax and $row < $tempmax) {$maxrow = $tempmax;}
;;   }
;;   $height = $maxrow - $row - 1;
;;   return $height;
;; }

;; #-----------------------------------------------------------------------------
;; # luetaan tiedostosta viimeisimm‰t qso:t ja luodaan duplikaattilista

;; sub read_last_qsos {
;;   my $hour = "";
;;   my $cleared = 0;
;;   $points = 0;
;;   $multipliers = 0;
;;   ($multi) = {};
;;   ($dupe) = {};
;;   @last_qsos = ();
;;   for (1..lastqso_listlen()) {push @last_qsos, '';}
;;   open FD, "<$logfile";
;;   while (my $row = <FD>) {
;;     chomp $row;
;;     my @qso = split(/[\s\t]/,$row);
;;     if ($hour ne substr($qso[1],0,2)) {
;;       $hour = substr($qso[1],0,2);
;;       ($dupe) = {};
;;     }
;;     if ($contest =~ /perus/) {
;;       my $min = substr($qso[1],2,2);
;;       if ($min >= 15 and $cleared == 0) {
;;         ($dupe) = {};
;;         $cleared++;
;;       }
;;       if ($min >= 30 and $cleared == 1) {
;;         ($dupe) = {};
;;         $cleared++;  
;;       }
;;       if ($min >= 45 and $cleared == 2) {
;;         ($dupe) = {};
;;         $cleared++;  
;;       }
;;     } 
;;     my $band = $bandswap->{$qso[9]};

;;     my $dupekey = "";
;;     if ($contest =~ /(kalakukko|sainio|syys|6cup)/) {
;;        $dupekey = $qso[2]."-".$band;
;;     } elsif ($contest =~ /(perus|nrau)/) {
;;        $dupekey = $qso[2];
;;     }

;;     if ($dupe->{$dupekey}) {
;;       $row = "DUPE$row";
;;       $row =~ s/^(DUPE)*/$1/;
;;     }
;;     $dupe->{$dupekey} = 1;

;;     points_and_multi($qso[2],$band,"$qso[7] $qso[8]");

;;     # kiertosana, jos kontesti ei ole peruskisa, nrau, kalakukko tai syysottelu
;;     if (length($qso[8]) == $wordlen->{$contest} and $contest !~ /kalakukko|perus|nrau|syys/) {
;;       substr($item->{outmsg},4,$wordlen->{$contest},$qso[8]);
;;     } else {
;;       substr($item->{outmsg},4,$wordlen->{$contest},$qso[5]);
;;     }
;;     $item->{lastout} = "$qso[4] $qso[5]";
;;     $item->{band} = $bandswap->{$qso[9]};
;;     $item->{mode} = substr("$qso[10] ",0,3);
;;     push @last_qsos, $row;
;;     ($qso_num = $row) =~ s/(^DUPE|^0+|\s.*)//g;
;;     while ($#last_qsos >= lastqso_listlen()) {
;;       shift @last_qsos;
;;     }
;;   }
;;   close FD;
;; }

;; #-----------------------------------------------------------------------------
;; # ja n‰lk‰vuoden mittainen main()

;; sub main {
;;   my $keybuf;
;;   my @termsize;
;;   my @oldsize;

;;   $main::term = Term::Cap::Tgetent Term::Cap { TERM => undef, OSPEED => 9600 };
;;   read_config();
;;   if ($conf->{mycall} eq "NOCALL") {
;;     print "\nLUE se dokumentaatio ja EDITOI konfiguraatio kuntoon ensin.\n\n";
;;     exit(42);
;;   }
;;   if ($contest =~ /(sainio|6cup)/) {
;;     read_dom();
;;   }
;;   if ($conf->{province} and $contest =~ /kalakukko/) {
;;     $item->{outmsg} .= $conf->{province};
;;   }

;;   $conf->{startserial} =~ s/^0*//;
;;   if ($contest eq "perus-k") {$conf->{startserial} = 101;}
;;   if ($contest eq "perus-s") {$conf->{startserial} = 201;}
;;   if ($contest eq "nrau") {$conf->{startserial} = 1;}
;;   if ($contest eq "perus-y") {$conf->{startserial} = 801;}
;;   if ($contest eq "perus-y") {$item->{workingmode} = "S&P";}

;;   $main::term->Tputs('cl',1,$stdout);
;;   foreach my $i (grep(/^_/, keys %$main::term)) {
;;     if ($main::term->{$i} ne "") {
;;       $termkeys->{$i} = $main::term->{$i};
;;     }
;;   }
;;   # kursorinsiirrin
;;   $_right_ = $termkeys->{_nd};

;;   Term::ReadKey::ReadMode(4);
;;   $position = length($item->{call});
;;   read_last_qsos();

;;   if ($contest =~ /(sainio|6cup)/) {
;;     substr($item->{outmsg},0,3,$conf->{ohc});
;;   } 
;;   if ($contest =~ /(kalakukko|nrau|syys|perus-[pksy])/) {
;;     substr($item->{outmsg},0,3,
;;        sprintf("%03d",($conf->{startserial}+$qso_num) % 1000));
;;   }

;;   clock(1);
;;   foreach my $i (keys %$item) {upd_scr($i);}
;;   go("call");
;;   go_pos();

;;   while (!$stopped) {

;;     clock();
;;     @termsize = Term::ReadKey::GetTerminalSize;
;;     if ("@termsize" ne "@oldsize") {
;;       if ($where ne "editor") {
;;         $editor_row = $#last_qsos+1;
;;       }
;;       refresh_screen();
;;       go($where);
;;       go_pos();
;;       @oldsize = @termsize;
;;     }

;;     while ((my $key = Term::ReadKey::ReadKey(-1)) ne undef) {
;;       $keybuf .= $key;
;;     }

;;     # ENTER in keyer field
;;     if (($keybuf eq "\r" or $keybuf eq "\r\n" or $keybuf eq "\n") 
;;         and $where eq "keyer") {
;;         cw_out($item->{keyer});
;;         $item->{status} = "OK";
;;         upd_scr("status");
;;         $keybuf = "\t";
;;     }

;;     # ENTER in editmode
;;     if (($keybuf eq "\r" or $keybuf eq "\r\n" or $keybuf eq "\n")
;;         and $editmode == 1) {

;;         my $band = $item->{band};
;;         $band = $bandswap->{$band};
;;         if ($item->{mode} eq "CW ") {
;;            $item->{rst_s} .= "9";
;;            $item->{rst_g} .= "9";
;;         }
 
;;         $item->{msg} =~ s/\s//g;
;;         my $county = substr($item->{msg},0,3);
;;         my $word = substr($item->{msg},3,5);
;;         if ($county eq "") {$county = "nul";}
;;         if ($word eq "") {$word = "null";}
 
;;         my $qsorow = "$editqso ";
;;         $qsorow .= sprintf("%s %s %s ",$editqso_utc,$item->{call},
;;                                        $item->{rst_s});
;;         $qsorow .= sprintf("%s %s ",$item->{outmsg},$item->{rst_g});
;;         $qsorow .= sprintf("%s %s %s %s",$county,$word,$band, $item->{mode});

;;         update_qso($editqso,$qsorow);
;;         $item->{status} = "QSO $editqso muutokset tallennettu";
;;         upd_scr("status");
;;         refresh_screen();
;;         $keybuf = $termkeys->{'_@7'};
;;     }

;;     # ENTER in call field
;;     if (($keybuf eq "\r" or $keybuf eq "\r\n" or $keybuf eq "\n")
;;         and $where eq "call") {
;;       $keybuf = "\t";
;;       $clock_stopped = 1;
;;       if ($item->{call} =~ /^OHO[HI]/) {
;;         $item->{call} =~ s/^OH//;
;;         upd_scr("call");
;;       }

;;       my $dupekey = "";
;;       if ($contest =~ /(kalakukko|sainio|syys|6cup)/) {
;;         $dupekey = $item->{call}."-".$item->{band};
;;       } elsif ($contest =~ /(perus|nrau)/) {
;;         $dupekey = $item->{call};
;;       }

;;       if ($dupe->{$dupekey}) {
;;         alert_dupe();
;;       } 

;;       if ($item->{mode} eq "CW " and length($item->{call}) > 2
;;           and !$dupe->{$dupekey}) {
;;         if ($item->{workingmode} eq "S&P") {
;;           cw_out($conf->{mycall});
;;         } else {
;;           cw_out($item->{call},cw_shorten($item->{rst_s}."9",$item->{outmsg}));
;;           $item->{lastout} = $item->{outmsg};
;;         }
;;       }
;;     }

;;     # ENTER in messagefield
;;     if (($keybuf eq "\r" or $keybuf eq "\r\n" or $keybuf eq "\n")
;;          and length($item->{call}) > 2 and $where eq "msg"
;;          and $item->{msg} ne "") {
;;       my @tim = gmtime();
;;       my $utc = sprintf("%02d%02d",$tim[2],$tim[1]);
;;       my $band = $item->{band};
;;       $band = $bandswap->{$band};
;;       if ($item->{mode} eq "CW ") {
;;          $item->{rst_s} .= "9";
;;          $item->{rst_g} .= "9";
;;       }

;;       $item->{msg} =~ s/\s//g;
;;       my $county = substr($item->{msg},0,3);
;;       my $word = substr($item->{msg},3,5);
;;       if ($county eq "") {$county = "nul";}
;;       if ($word eq "") {$word = "null";}

;;       my $qsorow = sprintf("%04d ",++$qso_num);
;;       if (($conf->{startserial}+$qso_num) % 1000 == 0) {
;;         $qsorow = sprintf("%04d ",++$qso_num);
;;       }

;;       $qsorow .= sprintf("%s %s %s ",$utc,$item->{call},$item->{rst_s});
;;       $qsorow .= sprintf("%s %s ",$item->{outmsg},$item->{rst_g});
;;       $qsorow .= sprintf("%s %s %s %s",$county,$word,$band, $item->{mode});

;;       my $dupekey = "";
;;       if ($contest =~ /(kalakukko|sainio|syys|6cup)/) {
;;         $dupekey = $item->{call}."-".$item->{band};
;;       } elsif ($contest =~ /(perus|nrau)/) {
;;         $dupekey = $item->{call};
;;       }
;;       if ($dupe->{$dupekey}) {
;;         alert_dupe();
;;         $qsorow = "DUPE$qsorow";
;;       }

;;       $item->{rst_s} = $item->{rst_g} = 59;
;;       if ($contest eq "nrau") {
;;         $item->{call} = "";
;;       } else {
;;         $item->{call} = "OH";
;;       }
;;       if ($contest =~ /(sainio|6cup)/) {
;;         my $gotcounty = substr($item->{msg},0,3);
;;         $item->{status} = "$gotcounty ".$countyname->{$gotcounty};
;;       }

;;       $position = length($item->{call});
;;       foreach my $i (keys %$item) {upd_scr($i);}
;;       $where = "call";
;;       go($where);
;;       go_pos();

;;       save_qso($qsorow);
;;       upd_scr("last_qsos");

;;       if ($item->{mode} eq "CW ") {
;;         if ($item->{workingmode} eq "S&P") {
;;           cw_out(cw_shorten("TU",$item->{rst_s}."9",$item->{outmsg}));
;;           $item->{lastout} = $item->{outmsg};
;;         } else {
;;           cw_out("TU",$conf->{mycall});
;;         }
;;       }

;;       if ($contest =~ /(kalakukko|perus-[pkys]|nrau|syys)/) {
;;         substr($item->{outmsg},0,3,
;;           sprintf("%03d",($conf->{startserial}+$qso_num) % 1000));
;;       }

;;       if (length($word) == $wordlen->{$contest} and $contest !~ /kalakukko|perus|syys|nrau/) {
;;         substr($item->{outmsg},4,$wordlen->{$contest},$word);
;;       }
;;       $item->{msg} = "";
;;       upd_scr("msg");
;;       upd_scr("outmsg");
;;     }

;;     # CTRL-C
;;     if ($keybuf eq "\3") {
;;       if (ask_quit()) {$stopped = 1;}
;;     }

;;     # META-M   
;;     if ($keybuf eq $conf->{metakey}."m") {
;;       if ($item->{mode} eq "CW ") {
;;         $item->{mode} = "SSB";
;;       } else {
;;         $item->{mode} = "CW ";
;;       }
;;       $item->{status} = "mode -> ".$item->{mode};
;;       upd_scr("mode");
;;       upd_scr("rst_s");
;;       upd_scr("rst_g");
;;       upd_scr("multi");
;;       upd_scr("status");
;;     } 

;;     # META-B
;;     if ($keybuf eq $conf->{metakey}."b" and $contest !~ /perus/) {
;;       if ($item->{band} eq "80") {
;;         $item->{band} = "40";
;;       } else {
;;         $item->{band} = "80";
;;       }
;;       upd_scr("band");
;;       upd_scr("multi");
;;       $item->{status} = "band -> ".$item->{band};
;;       upd_scr("status");
;;     }

;;     # META-K (keyer)
;;     if ($keybuf eq $conf->{metakey}."k") {
;;       go("status");
;;       $where = "keyer";
;;       $item->{status} = color('black on_green') . "keyer >" . color('reset');
;;       upd_scr("status");
;;       go("keyer");
;;       upd_scr("keyer");
;;       $position = length($item->{$where});
;;       go_pos();
;;     }

;;     # BACKSPACE
;;     if ($keybuf eq chr(8) or $keybuf eq chr(127)) {
;;       if ($position > 0) {
;;         $position--;
;;         if ($where eq "call" or $where eq "msg" or $where eq "outmsg" 
;;              or $where eq "keyer") {
;;           if ($where eq "call") {
;;             clear_status();
;;             $clock_stopped = 0;
;;           }
;;           substr($item->{$where},$position,1,"");
;;           upd_scr($where);
;;           go($where);
;;           go_pos();
;;         }
;;       }
;;     }

;;     # DEL
;;     if ($keybuf eq $main::term->{_kD}) {
;;       if ($where eq "call") {
;;         clear_status();
;;         $clock_stopped = 0;
;;       }
;;       substr($item->{$where},$position,1,"");
;;       upd_scr($where);
;;       go($where);
;;       go_pos();
;;     }

;;     # METAKEY
;;     if ($keybuf eq $conf->{metakey}) {
;;       if ($where ne "keyer") {
;;         $item->{status} = "(META)";
;;         upd_scr("status");
;;       }
;;     }

;;     # META-ESC (stop CW output) 
;;     if ($keybuf eq $conf->{metakey}."\e") {
;;       if ($item->{mode} eq "CW ") {
;;         cw_out("\e4");
;;       }
;;       if ($where eq "keyer") {
;;         $keybuf = "\t";
;;       }
;;       $item->{status} = "CW-stop";
;;       upd_scr("status");
;;     }

;;     # UP-KEY
;;     if ($keybuf eq $termkeys->{_up}) {
;;       if ($editmode == 0) {
;;         $editmode = 1;
;;         %$backup_item = %$item;
;;       }
;;       $editor_row--;
;;       if ($last_qsos[$editor_row] eq "") {$editor_row++;}

;;       qso_to_item();
;;       foreach my $i ("band","mode","call","rst_g","rst_s",
;;                      "outmsg","msg","last_qsos") {
;;         upd_scr($i);
;;       }
;;     }

;;     # DOWN-KEY or END-KEY
;;     if (($keybuf eq $termkeys->{_do} or $keybuf eq $termkeys->{'_@7'}) 
;;          and $editmode == 1) {
;;       if ($keybuf eq $termkeys->{'_@7'}) {$editor_row = $#last_qsos;}
;;       $editor_row++;
;;       $item->{call} = $editor_row;
;;       if ($editor_row > $#last_qsos) {
;;         $editor_row = $#last_qsos + 1;
;;         $editmode = 0;
;;         %$item = %$backup_item;
;;         $where = "call";
;;         go("call");
;;         $position = length($item->{call});
;;         go_pos();
;;       } else {
;;         qso_to_item();
;;       }
;;       foreach my $i ("band","mode","call","rst_g","rst_s",
;;                      "outmsg","msg","last_qsos") {
;;         upd_scr($i);
;;       }
;;     } 

;;     # LEFT-KEY
;;     if ($keybuf eq $termkeys->{_kl} or $keybuf eq $termkeys->{_le} 
;;         or $keybuf eq "\e[D") {
;;       if ($position > 0) {
;;         $position--;
;;         upd_scr($where);
;;         go($where);
;;         go_pos();
;;       }
;;     }   

;;     # RIGHT-KEY
;;     if ($keybuf eq $termkeys->{_kr} or $keybuf eq $termkeys->{_nd}
;;         or $keybuf eq "\e[C") {
;;       if ($position < length($item->{$where})) {
;;         $position++;
;;         upd_scr($where);
;;         go($where);
;;         go_pos();
;;       }
;;     }   

;;     # PGUP
;;     if ($keybuf eq $termkeys->{_kP}) {
;;       if ($where eq "call" and $item->{rst_s} < 59) {
;;         $item->{rst_s} += 1;
;;         upd_scr("rst_s");
;;       }
;;       if ($where eq "msg" and $item->{rst_g} < 59) {
;;         $item->{rst_g} += 1;
;;         upd_scr("rst_g");
;;       }
;;     }

;;     # PGDN
;;     if ($keybuf eq $termkeys->{_kN}) {
;;       if ($where eq "call" and $item->{rst_s} > 51) {
;;         $item->{rst_s} -= 1;
;;         upd_scr("rst_s");
;;       }
;;       if ($where eq "msg" and $item->{rst_g} > 51) {
;;         $item->{rst_g} -= 1;
;;         upd_scr("rst_g");
;;       }
;;     }

;;     # TAB or space when editing call
;;     if ($keybuf eq "\t" or ($keybuf eq " " and $where eq "call")) {
;;       if ($where eq "call") {
;;         $keybuf = "\t";
;;         $where = "msg";
;;       } elsif ($where eq "msg") {
;;         $where = "outmsg";  
;;       } else {
;;         $where = "call";
;;         clear_status();
;;       }
;;       go($where);
;;       $position = length($item->{$where});
;;       go_pos();
;;     }

;;     # - (delete last qso)
;;     if ($keybuf eq "-" and $where ne "keyer") {
;;       if (ask_del_last()) {
;;         refresh_screen();
;;       }
;;       $keybuf = undef;
;;       $where = "call";
;;       go($where);
;;       $position = length($item->{$where});
;;       go_pos();
;;     }

;;     # ? (help)
;;     if ($keybuf eq "?" and $where ne "keyer") {
;;       help_win();
;;       refresh_screen();
;;       $keybuf = undef;
;;       go($where);
;;       go_pos();
;;     }

;;     # + (toggle working mode)
;;     if ($keybuf eq "+" and $where ne "keyer" and $contest ne "perus-y") {
;;       if ($item->{workingmode} eq "CQ") {
;;         $item->{workingmode} = "S&P";
;;       } else {
;;         $item->{workingmode} = "CQ";
;;       }
;;       upd_scr("workingmode");
;;     }

;;     # META-R (last message again)
;;     if ($keybuf eq $conf->{metakey}."r" and $where ne "keyer") {
;;       if ($item->{mode} eq "CW ") {
;;         cw_out($item->{lastout});
;;       }
;;     }
  
;;     # META-N (last number again)
;;     if ($keybuf eq $conf->{metakey}."n" and $where ne "keyer") {
;;       if ($item->{mode} eq "CW ") {
;;         cw_out(substr($item->{lastout},0,3));
;;       }
;;     }

;;     # META-S (last word again)
;;     if ($keybuf eq $conf->{metakey}."s" and $where ne "keyer") {
;;       if ($item->{mode} eq "CW ") {
;;         cw_out(substr($item->{lastout},4)); 
;;       }
;;     }  

;;     # CTRL-U (clear field)
;;     if ($keybuf eq "\x15") { 
;;       $position = 0;
;;       $item->{$where} = "";
;;       upd_scr($where);
;;       go($where);
;;       go_pos();
;;     }     

;;     # CTRL-L (refresh screen)
;;     if ($keybuf eq "\f") {
;;       refresh_screen();
;;       go($where);
;;       go_pos();
;;     }

;;     # CTRL-R (reload conf)
;;     if ($keybuf eq "\022") {
;;       read_config();  
;;       upd_scr("helps");
;;       $item->{status} = "reload ok";
;;       upd_scr("status");
;;     }

;;     # META-n MACROKEYS
;;     foreach my $i (1,2,3,4,5,6,7,8,9,0) {
;;       my $j = $conf->{metakey}."$i";
;;       if ($keybuf eq $j and $item->{mode} eq "CW ") {
;;         $j = "meta-$i";
;;         cw_out($conf->{$j});
;;       }
;;     }

;;     if ($keybuf =~ m/^[a-zA-Z‰ˆÂƒ÷≈_]$/) {
;;       $keybuf =~ tr#a-zÂ‰ˆ_#A-Z≈ƒ÷/#;
;;     }

;;     if ($where eq "call" and $position < $fieldlen->{$where} 
;;           and $keybuf =~ m#^[\dA-Z/]$#) {
;;       $clock_stopped = 0;
;;       substr($item->{call},$position,1,$keybuf);
;;       $position++;
;;       upd_scr("call");
;;       go("call");
;;       go_pos();
;;     }
;;     if ($where =~ m#msg$# and $position < $fieldlen->{$where} 
;;            and $keybuf =~ m#^[\dA-Zƒ÷≈/ ]$#) {
;;       substr($item->{$where},$position,1,$keybuf);
;;       $position++;
;;       upd_scr($where);
;;       go($where);
;;       go_pos();
;;     }
;;     if ($where eq "keyer" and $position < $fieldlen->{$where} 
;;          and $keybuf =~ m/^[\da-zA-Z‰ˆÂƒ÷≈\+\-\/\*=<>!&\(\? ]$/) {
;;       substr($item->{$where},$position,1,$keybuf);
;;       $position++;
;;       upd_scr($where);
;;       go($where);
;;       go_pos();
;;     }

;;     if ($keybuf ne $conf->{metakey}) {
;;       $keybuf = undef;
;;       if ($item->{status} eq "(META)") {clear_status();}
;;     }
;;     if ($keybuf ne "\e") {
;;       select(undef,undef,undef,0.01);
;;     }
;;   }
;;   Term::ReadKey::ReadMode(0);
;;   return 0;
;; }
;; #-----------------------------------------------------------------------------
;; # no tehd‰‰n se!
;; exit main();
