package myproject;

import com.pulumi.Pulumi;
import com.pulumi.aws.AwsFunctions;
import com.pulumi.aws.ec2.*;
import com.pulumi.aws.ec2.inputs.RouteTableRouteArgs;
import com.pulumi.aws.inputs.GetAvailabilityZonesArgs;
import com.pulumi.core.Output;
import com.pulumi.aws.s3.Bucket;

import java.util.Map;
import java.util.Optional;

public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            var config = ctx.config();
//            Optional<String> profileOption = config.getObject("aws:region");
            var data = config.requireObject("data",Map.class);
            var region = config.get("aws:region");
            System.out.println(region);
            System.out.println(region);
            var availablezones = AwsFunctions.getAvailabilityZones(GetAvailabilityZonesArgs.builder().state("available").build());
            System.out.println("Avaialble zones "+availablezones);
            String vpcname = "webapp-vpc";
            String vpccidr = data.get("vpcCidr").toString();
            Vpc vpc = new Vpc(vpcname, new VpcArgs.Builder()
                    .cidrBlock(vpccidr)
                    .tags(Map.of("Name", vpcname))
                    .build());
            InternetGateway ig = new InternetGateway("internetGateway", new InternetGatewayArgs.Builder()
                    .vpcId(vpc.id())
                    .tags(Map.of("Name", vpcname + "-ig"))
                    .build());
            RouteTable publicroutetable = new RouteTable("publicRouteTable", new RouteTableArgs.Builder()
                    .vpcId(vpc.id())
                    .tags(Map.of("Name", vpcname + "-publicroutetable"))
                    .routes(RouteTableRouteArgs.builder().cidrBlock("0.0.0.0/0").gatewayId(ig.id()).build())
                    .build());
            Subnet webapp_public_subnet = new Subnet("webapp" , new SubnetArgs.Builder()
                    .vpcId(vpc.id())
                    .mapPublicIpOnLaunch(true)
                    .availabilityZone("us-east-1a") // Should remove hard coding
                    .cidrBlock("10.0.0.0/24")
                    .tags(Map.of("Name", vpcname + "webapp"))
                    .build());
            RouteTableAssociation routeTableAssocpub = new RouteTableAssociation("pubroutetableassoc" , new RouteTableAssociationArgs.Builder()
                    .subnetId(webapp_public_subnet.id())
                    .routeTableId(publicroutetable.id())
                    .build()
            );
            Subnet db_private_subnet = new Subnet("db" , new SubnetArgs.Builder()
                    .vpcId(vpc.id())
                    .availabilityZone("us-east-1a") // Should remove hard coding
                    .cidrBlock("10.0.1.0/24")
                    .tags(Map.of("Name", vpcname + "db"))
                    .build());
        });
    }
}
