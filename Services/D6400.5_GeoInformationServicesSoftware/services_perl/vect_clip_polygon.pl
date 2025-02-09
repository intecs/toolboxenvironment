#!/usr/bin/perl 

use Getopt::Long;
use Switch;
use strict;
use warnings;

use FindBin ();
use lib "$FindBin::Bin/lib";
use Utils;
use OGRUtils;

#general parameters
my %params = Utils::getParams();
my $project_dir=$params{'project_dir'};
my $debug=$params{'debug'};


# read input parameters:
#
# = indicates mandatory options
# : indicates optional options
#
GetOptions(
	"unique_code=s" => \my $unique_code,
	"input_file=s" => \my $input_file,
	"input_mimetype:s" => \my $input_mimetype,
	"input_polygon=s" => \my $input_polygon,
	"output_file=s" => \my $output_file,
	"output_mimetype:s" => \my $output_mimetype,
	"output_types=s" => \my $output_types
);

my @output_types_array = ();
if ($output_types) {
	@output_types_array = split(/,/,$output_types);
}


##########################
# Check input parameters #
##########################

#1. check mandatory parameters
if (!(defined ($unique_code) && defined ($input_file) && defined ($input_polygon) && defined ($output_file))) {
	print "One or more parameters are missing or invalid (string were integer is expected)\n";
	exit 1;
}


# 2. check if all output types are one of the following: point, kernel, centroid, line, boundary, area, face
for my $output_type (@output_types_array) {
	my @valid_output_type = ("point", "kernel", "centroid", "line", "boundary", "area", "face");
	if (! (grep $_ eq $output_type, @valid_output_type) ) {
		print "Only the following output types are allowed: point, kernel, centroid, line, boundary, area and face\n";
		exit 1;
	}
} 

#################
# Set variables #
#################

my $location="Location_$unique_code";

#derived parameters
my $work_dir= "$project_dir/work/$unique_code";

# create working directory
mkdir($work_dir, 0755) or die "cannot mkdir $work_dir: $!";


my $output_format="";
if (defined($output_mimetype) && $output_mimetype ne "") {
	print "Set output format\n";
	print "Output MimeType: $output_mimetype\n";
	$output_format = OGRUtils::getOGRFormatByMimeTypeInGrassStyle($output_mimetype);
	if ($output_format ne "") {
		$output_format = "format=\"$output_format\"";
	}
	print "Output format: $output_format\n";
}

#If MapInfo File is the output format, specify that we want to create MIF/MID files
#Otherwise, TAB files are created
my $dataset_creation_option = "";

if ($output_format eq "format=\"MapInfo File\"") {
	$dataset_creation_option = "dsco FORMAT=MIF";
}

if (! defined ($output_types)) {
	$output_types = "";
} elsif ($output_types ne "") {
	$output_types = "type=$output_types";
}


##############
# Processing #
##############

Utils::printStart($unique_code, $debug);

if($input_mimetype eq "application/x-esri-shapefile") {
	my $output_dir= "$work_dir/vector";
	$input_file = Utils::unzip($input_file, $output_dir, $debug);
}

# import vector into new location
Utils::execute("v.in.ogr dsn=\"$input_file\" output=vector location=$location", $debug);

# change location
Utils::execute("g.mapset mapset=PERMANENT location=$location", $debug);

# import polygon  (-o: override projection check because GRASS does not recognize
# the projection of the GML and will complain if the -o option is missing)
Utils::execute("v.in.ogr -o dsn=\"$input_polygon\" output=polygon", $debug);

# select features from ainput by features from binput
Utils::execute("v.overlay ainput=vector binput=polygon output=output_vector operator=and", $debug);

#if output is shapefile, ZIP it
if ( (!defined($output_mimetype)) && $output_mimetype eq "" || $output_mimetype eq "application/x-esri-shapefile") {
	
	# create output directory
	my $output_dir = "$work_dir/output";
	mkdir($output_dir, 0755) or die "cannot mkdir $output_dir: $!";
	
	# create output
	Utils::execute("v.out.ogr input=output_vector dsn=$output_dir $output_format $dataset_creation_option $output_types", $debug);
	
	# zip the shapefiles
	Utils::zip("zip $output_file *", $output_dir, $output_file, $debug);
	
} else {
	# create output
	Utils::execute("v.out.ogr input=output_vector dsn=$output_file $output_format $dataset_creation_option $output_types", $debug);
}

Utils::printEnd($unique_code, $debug);

