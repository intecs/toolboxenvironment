#!/usr/bin/perl 

use Getopt::Long;
use Switch;
use strict;
use warnings;

use FindBin ();
use lib "$FindBin::Bin/lib";
use Utils;
use GDALUtils;


#general parameters
my %params = Utils::getParams();
my $project_dir=$params{'project_dir'};
my $perl_scripts_dir=$params{'perl_scripts_dir'};
my $debug=$params{'debug'};

	
# read input parameters:
#
# = indicates mandatory options
# : indicates optional options
#
GetOptions(
	"unique_code=s" => \my $unique_code,
	"input_raster_file=s" => \my $input_raster_file,
	"input_polygon_file=s" => \my $input_polygon_file,
	"input_polygon_mimetype:s" => \my $input_polygon_mimetype,
	"crs:s" => \my $crs,
	"output_file=s" => \my $output_file,
	"output_mimetype:s" => \my $output_mimetype
);


##########################
# Check input parameters #
##########################

#1. check mandatory parameters
if (!(defined ($unique_code) && defined ($input_raster_file) && defined ($input_polygon_file) && defined ($output_file) )) {
	print "One or more parameters are missing or invalid (string were integer is expected)\n";
	exit 1;
}


#################
# Set variables #
#################

#derived parameters
my $work_dir = "$project_dir/work/$unique_code";

# default starspan output img file = output_starspan__mr0001.img
# (will be overridden if the input polygon is a shapefile
#  in that case the output file will be output_starspan__mr0000.img)
my $starspan_output_img_file="output_starspan__mr0001.img";


# create working directory
mkdir($work_dir, 0755) or die "cannot mkdir $work_dir: $!";


my $location="Location_$unique_code";

my $output_format="";
if (defined($output_mimetype) && $output_mimetype ne "") {
	print "Set output format\n";
	print "Output MimeType: $output_mimetype\n";
	$output_format = GDALUtils::getGDALFormatByMimeType($output_mimetype);
	if ($output_format ne "") {
		$output_format = "-of $output_format";
	}
	print "Output format: $output_format\n";
}

my $output_crs="";
if (defined($crs) && $crs ne "") {
	$output_crs = "-a_srs $crs";
}

#if (defined($output_data_type) && $output_data_type ne "") {
#	$output_data_type="-ot $output_data_type";
#} else {
#	$output_data_type="";
#}


##############
# Processing #
##############

Utils::printStart($unique_code, $debug);

if (defined($input_polygon_mimetype) && $input_polygon_mimetype eq "application/x-esri-shapefile") {
	my $output_dir="$work_dir/vector";
	$input_polygon_file = Utils::unzip($input_polygon_file, $output_dir, $debug);
	
	#if input polygon is a shapefile, the file generated by starspan will
	# be named output_starspan__mr0000.img instead of output_starspan__mr0001.img
	$starspan_output_img_file="output_starspan__mr0000.img";
}


# create output directory
my $output_starspan = "$work_dir/output_starspan";
mkdir($output_starspan, 0755) or die "cannot mkdir $output_starspan: $!";

performStarspan("$output_starspan", $input_polygon_file, $input_raster_file);

# convert to correct output format
my $gdal_translate_command = "gdal_translate $output_crs $output_format \"${output_starspan}/${starspan_output_img_file}\" \"$output_file\"";
Utils::execute($gdal_translate_command, $debug);

Utils::printEnd($unique_code, $debug);


exit;


sub performStarspan {
	my ($output_dir, $input_vector_file, $input_raster_file) = @_;

	#starspan2 info:
	# If –-in is given, pixels not contained in the geometry feature are nullified in the resulting mini raster. By default, all pixels in the feature envelope (bounding box) are retained. 
	#starspan2 --vector "/var/www/html/genesis/data/GIM_test_data/gml/gml2_south_france.gml" --raster "/var/www/html/genesis/data/ACRI/GENESIS-Data/CWQ-Others/L3_OSTIA_OSTIA_SST_j__20090326_PACA_PC_ACR___0000.tif" --out-prefix output_starspan_ --out-type mini_rasters --in
	my $starspan_command="starspan2 --vector $input_vector_file --raster $input_raster_file --out-prefix output_starspan_ --out-type mini_rasters --in;";
	print "STARSPAN\n";
	Utils::execute($starspan_command, $debug);

	# output:
	# input=GML
	#  output_starspan__mr0001.hdr
	#  output_starspan__mr0001.img
	#  output_starspan__mr0001.img.aux.xml
	# input=shapefile
	#  output_starspan__mr0000.hdr
	#  output_starspan__mr0000.img
	#  output_starspan__mr0000.img.aux.xml
	
	# move output
	Utils::execute("mv output_starspan_* ${output_dir}/", $debug);
	
	
	
	#remove generated files
	unlink "output_starspan_*";
}



