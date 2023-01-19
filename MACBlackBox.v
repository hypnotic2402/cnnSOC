module MACBlackBox
  #(parameter WIDTH)
   (
    input 		   clock,
    input 		   reset,
    input 		   input_valid,
    input 		   output_ready,
    input [WIDTH-1:0]	   x,
    input [WIDTH-1:0]      y,
    output reg [WIDTH-1:0] mac,
    output 		   input_ready,
    output		   output_valid,
    output		   busy
   );

   localparam S_IDLE = 2'b00 , S_RUN = 2'b01 , S_DONE = 2'b10;
   
   reg [1:0]		   state;
   reg [1:0]		   tmp;
   
   assign input_ready = state == S_IDLE;
   assign output_valid = state == S_DONE;
   assign busy = state != S_IDLE;

   always @(posedge clock) begin
      if (reset)
	state <= S_IDLE;
      else if (state == S_IDLE && input_valid)
	state <= S_RUN;
      else if (state == S_RUN && tmp == 0)
	state <= S_DONE;
      else if (state == S_DONE && output_ready)
	state <= S_IDLE;
   end

   always @(posedge clock) begin
      if (state == S_IDLE && input_valid) begin
	tmp <= 1'b1;
      end else if (state == S_RUN) begin
	mac <= mac + x*y;
	tmp <= 1'b0;
      end
   end

endmodule

	
