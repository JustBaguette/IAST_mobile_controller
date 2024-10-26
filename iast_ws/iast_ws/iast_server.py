import asyncio
import websockets
import json
import rclpy
from rclpy.node import Node
from geometry_msgs.msg import Twist
import math

# Server configuration
IP_ADDRESS = "192.168.1.35"  # Replace with your server's IP address
PORT = 3000

class WebSocketToCmdVelNode(Node):
    def __init__(self):
        super().__init__('websocket_to_cmd_vel')
        self.publisher = self.create_publisher(Twist, 'cmd_vel', 10)
        self.loop = asyncio.get_event_loop()
        self.loop.run_until_complete(self.start_server())

    async def handle_connection(self, websocket, path):
        self.get_logger().info("Client connected")

        # Send a device status message when a new client connects
        device_status = {
            "conection": "true",
            "devices": json.dumps({"mobile": "true"})  # Indicates mobile is connected
        }
        await websocket.send(json.dumps(device_status))

        try:
            async for message in websocket:
                # Parse the incoming JSON message
                data = json.loads(message)

                # Extract speed and angle values if they exist in the message
                speed = data.get("sp")
                angle = data.get("m")

                if speed is not None and angle is not None:
                    self.get_logger().info(f"Received data -> Speed: {speed}, Angle: {angle}")

                    # Create a Twist message
                    twist = Twist()
                    # Assuming speed corresponds to linear.x and angle corresponds to angular.z
                    twist.linear.x = speed * math.cos(math.radians(angle)) / 40
                    twist.angular.z = speed * math.sin(math.radians(angle)) / 40
                    self.get_logger().info(f"Published Twist: linear.x={twist.linear.x}, angular.z={twist.angular.z}")
                    self.publisher.publish(twist)

                    # Optional: Send an acknowledgment back to the client
                    response = {"status": "received"}
                    await websocket.send(json.dumps(response))

        except websockets.exceptions.ConnectionClosed as e:
            self.get_logger().info(f"Connection closed: {e}")

    async def start_server(self):
        self.get_logger().info(f"Server starting on ws://{IP_ADDRESS}:{PORT}")
        async with websockets.serve(self.handle_connection, IP_ADDRESS, PORT):
            await asyncio.Future()  # Run forever

def main(args=None):
    rclpy.init(args=args)
    node = WebSocketToCmdVelNode()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.shutdown()

if __name__ == "__main__":
    main()
