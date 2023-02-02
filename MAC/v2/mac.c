#include "mmio.h"

#define MAC_STATUS 0x1000
#define MAC_X 0x1004
#define MAC_Y 0x1008
#define MAC_MAC 0x100C

unsigned int mac_ref(unsigned int x1, unsigned int y1, unsigned int x2, unsigned int y2 ,  unsigned int mac) {
//   while (y != 0) {
//     if (x > y)
//       x = x - y;
//     else
//       y = y - x;
//   }
//   return x;

    mac = 0;

    mac = mac + (x1 * y1);
    mac = mac + (x2 * y2);

    return mac;

}

int main(void)
{
  uint32_t result, ref, x1 = 2, y1 = 3 , x2 = 2 , y2 = 5;

  // wait for peripheral to be ready
  while (reg_read8(MAC_STATUS)) ;


  reg_write32(MAC_MAC , 0);

  reg_write32(MAC_X, x1);
  reg_write32(MAC_Y, y1);

  while (reg_read8(MAC_STATUS)) ;

  reg_write32(MAC_X, x1);
  reg_write32(MAC_Y, y1);

  while (reg_read8(MAC_STATUS)) ;


  // wait for peripheral to complete
//   while ((reg_read8(GCD_STATUS) & 0x1) == 0) ;

  result = reg_read32(MAC_MAC);
  ref = mac_ref(x1, y1 , x2 , y2);

  if (result != ref) {
    printf("Hardware result %d does not match reference value %d\n", result, ref);
    return 1;
  }
  printf("Hardware result %d is correct for MAC\n", result);
  return 0;
}