package myproject;

import com.pulumi.Pulumi;
import com.pulumi.aws.AwsFunctions;
import com.pulumi.aws.ec2.*;
import com.pulumi.aws.ec2.inputs.InstanceEbsBlockDeviceArgs;
import com.pulumi.aws.ec2.inputs.RouteTableRouteArgs;
import com.pulumi.aws.ec2.inputs.SecurityGroupEgressArgs;
import com.pulumi.aws.ec2.inputs.SecurityGroupIngressArgs;
import com.pulumi.aws.inputs.GetAvailabilityZonesArgs;

import java.util.Collections;
import java.util.Map;


public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            var config = ctx.config();
//            Optional<String> profileOption = config.getObject("aws:region");
            var data = config.requireObject("data", Map.class);
            var region = config.get("aws:region");
            System.out.println(region);
            System.out.println(region);
            var availablezones = AwsFunctions.getAvailabilityZones(GetAvailabilityZonesArgs.builder().state("available").build());
            System.out.println("Avaialble zones " + availablezones);
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
            Subnet webapp_public_subnet = new Subnet("webapp", new SubnetArgs.Builder()
                    .vpcId(vpc.id())
                    .mapPublicIpOnLaunch(true)
                    .availabilityZone("us-east-1a") // Should remove hard coding
                    .cidrBlock("10.0.0.0/24")
                    .tags(Map.of("Name", vpcname + "webapp"))
                    .build());
            RouteTableAssociation routeTableAssocpub = new RouteTableAssociation("pubroutetableassoc", new RouteTableAssociationArgs.Builder()
                    .subnetId(webapp_public_subnet.id())
                    .routeTableId(publicroutetable.id())
                    .build()
            );
            Subnet db_private_subnet = new Subnet("db", new SubnetArgs.Builder()
                    .vpcId(vpc.id())
                    .availabilityZone("us-east-1a") // Should remove hard coding
                    .cidrBlock("10.0.1.0/24")
                    .tags(Map.of("Name", vpcname + "db"))
                    .build());

//
            SecurityGroup appSecurityGroup = new SecurityGroup("application_security_group", new SecurityGroupArgs.Builder()
                    .ingress(SecurityGroupIngressArgs.builder()
                            .protocol("tcp")
                            .fromPort(80)
                            .toPort(80)
                            .cidrBlocks(Collections.singletonList("0.0.0.0/0"))
                            .build())
                    .ingress(SecurityGroupIngressArgs.builder()
                            .protocol("tcp")
                            .fromPort(22)
                            .toPort(22)
                            .cidrBlocks(Collections.singletonList("0.0.0.0/0"))
                            .build())
                    .ingress(SecurityGroupIngressArgs.builder()

                            .protocol("tcp")
                            .fromPort(8000)
                            .toPort(8000)
                            .cidrBlocks(Collections.singletonList("0.0.0.0/0"))
                            .build())
                    .egress(SecurityGroupEgressArgs.builder()
                            .protocol("-1")
                            .fromPort(0)
                            .toPort(0)
                            .cidrBlocks(Collections.singletonList("0.0.0.0/0"))
                            .build())
                    .vpcId(vpc.id())
                    .build());

            String userData = "#!/bin/bash\n"
                    + "sudo chmod 770 /home/ec2-user/fast-api-webapp-0.0.1.zip\n"
                    + "sudo unzip -o fast-api-webapp-0.0.1.zip\n"
                    + "cd fast-api-webapp-0.0.1\n"
                    + "sudo python3.11 -m venv venv\n"
                    + "source venv/bin/activate\n"
                    + "sudo chown -R ec2-user:ec2-user /home/ec2-user/fast-api-webapp-0.0.1/venv\n"
                    + "cd app\n"
                    + "pip3.11 install -r requirements.txt\n"
                    + "cd /home/ec2-user/fast-api-webapp-0.0.1/venv/bin\n"
                    + "sudo cp /tmp/webservice.service /etc/systemd/system\n"
                    + "sudo chmod 770 /etc/systemd/system/webservice.service\n"
                    + "sudo systemctl start webservice.service\n"
                    + "sudo systemctl enable webservice.service\n"
                    + "sudo systemctl restart webservice.service\n"
                    + "sudo systemctl status webservice.service\n"
                    + "echo '****** Copied webservice! *******'\n";

            Double volume = (Double) data.get("volume");
            Instance instance = new Instance("devec2", new InstanceArgs.Builder()
                    .ami(data.get("AmiId").toString())
                    .instanceType("t2.micro")
                    .keyName(data.get("Keyname").toString())
                    .ebsBlockDevices(InstanceEbsBlockDeviceArgs.builder()
                            .deviceName("/dev/xvda")
                            .volumeType("gp2")
                            .volumeSize(volume.intValue())
                            .deleteOnTermination(true)
                            .build())
                    .vpcSecurityGroupIds(appSecurityGroup.id().applyValue(Collections::singletonList))
                    .subnetId(webapp_public_subnet.id())
                    .disableApiTermination(false)
//                    .iamInstanceProfile(instanceProfile.name())
                    .tags(Map.of("Name", "ec2dev"))
                    .userData(userData)
                    .build());
        });
    }
}
