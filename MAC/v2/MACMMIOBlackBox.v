module MACBlackBox
    #(parameter WIDTH)
   (
    input                  clock,
    input                  reset,
    input [WIDTH-1:0]      x,
    input [WIDTH-1:0]      y,
    output reg [WIDTH-1:0] mac,
    output                 busy
    );

    always  @(posedge clock) begin
        if (!busy) begin
            busy = !busy;
            mac <= mac + x*y;
            busy = !busy;
        end
    end

endmodule